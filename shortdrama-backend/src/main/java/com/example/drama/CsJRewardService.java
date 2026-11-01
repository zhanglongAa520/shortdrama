package com.example.drama;

import com.example.drama.CsJRewardDto.AttemptResponse;
import com.example.drama.CsJRewardDto.AttemptStatusResponse;
import com.example.drama.CsJRewardDto.ConfigResponse;
import com.example.drama.CsJRewardDto.PlatformConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CsJRewardService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CsJProperties properties;
    private final IntegrationConfigService integrations;
    private final RewardAttemptRepository attempts;
    private final AppUserRepository users;
    private final EpisodeRepository episodes;
    private final EpisodeEntitlementService entitlements;

    public CsJRewardService(CsJProperties properties, IntegrationConfigService integrations,
                            RewardAttemptRepository attempts,
                            AppUserRepository users, EpisodeRepository episodes,
                            EpisodeEntitlementService entitlements) {
        this.properties = properties;
        this.integrations = integrations;
        this.attempts = attempts;
        this.users = users;
        this.episodes = episodes;
        this.entitlements = entitlements;
    }

    public ConfigResponse config() {
        Map<RewardPlatform, PlatformConfig> platforms = new EnumMap<>(RewardPlatform.class);
        boolean anyEnabled = false;
        for (RewardPlatform platform : RewardPlatform.values()) {
            IntegrationConfigService.EffectiveConfig configured = platformConfig(platform);
            boolean ready = integrations.isConfigured(configured);
            boolean enabled = configured.enabled() && ready;
            anyEnabled |= enabled;
            platforms.put(platform, new PlatformConfig(
                    enabled,
                    ready,
                    ready ? configured.value("appId") : null,
                    ready ? configured.value("placementId") : null));
        }
        return new ConfigResponse(anyEnabled, platforms);
    }

    @Transactional
    public AttemptResponse createAttempt(String username, Long episodeId, RewardPlatform platform) {
        IntegrationConfigService.EffectiveConfig platformConfig = requireEnabled(platform);
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Episode episode = episodes.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在"));
        if (episode.getDrama().getStatus() != DramaStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在");
        }
        if (entitlements.canWatch(user, episode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前分集无需通过广告解锁");
        }

        Instant now = Instant.now();
        RewardAttempt attempt = new RewardAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setUser(user);
        attempt.setEpisode(episode);
        attempt.setPlatform(platform);
        attempt.setNonce(randomNonce());
        attempt.setStatus(RewardAttemptStatus.PENDING);
        attempt.setExpectedRewardName(platformConfig.value("rewardName"));
        attempt.setExpectedRewardAmount(Integer.parseInt(platformConfig.value("rewardAmount")));
        attempt.setCreatedAt(now);
        attempt.setExpiresAt(now.plus(validTtl()));
        attempts.save(attempt);
        return new AttemptResponse(attempt.getId(), attempt.getStatus(), platform, episode.getId(),
                user.getId().toString(), attempt.getNonce(), attempt.getExpiresAt());
    }

    @Transactional
    public AttemptStatusResponse status(String username, String attemptId) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        RewardAttempt attempt = attempts.findByIdAndUserId(attemptId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "奖励任务不存在"));
        if (attempt.getStatus() == RewardAttemptStatus.PENDING
                && !attempt.getExpiresAt().isAfter(Instant.now())) {
            attempt.setStatus(RewardAttemptStatus.EXPIRED);
        }
        return statusResponse(attempt);
    }

    @Transactional
    public boolean callback(String userId, String transactionId, String rewardName,
                            String rewardAmountValue, String extra, String sign) {
        if (!validCallbackShape(userId, transactionId, rewardName, rewardAmountValue, extra, sign)) return false;
        RewardAttempt attempt = attempts.findByNonceForUpdate(extra).orElse(null);
        if (attempt == null) return false;
        IntegrationConfigService.EffectiveConfig platformConfig = enabledPlatform(attempt.getPlatform());
        if (platformConfig == null
                || !secureEquals(signature(platformConfig.value("securityKey"), transactionId), sign)) {
            return false;
        }

        int rewardAmount;
        try {
            rewardAmount = Integer.parseInt(rewardAmountValue);
        } catch (NumberFormatException exception) {
            return false;
        }
        boolean bindingMatches = attempt.getUser().getId().toString().equals(userId)
                && attempt.getExpectedRewardName().equals(rewardName)
                && attempt.getExpectedRewardAmount() == rewardAmount
                && attempt.getExpectedRewardName().equals(platformConfig.value("rewardName"))
                && attempt.getExpectedRewardAmount() == Integer.parseInt(platformConfig.value("rewardAmount"));
        if (!bindingMatches) return false;

        if (attempt.getStatus() == RewardAttemptStatus.GRANTED) {
            return transactionId.equals(attempt.getProviderTransactionId());
        }
        if (attempt.getStatus() != RewardAttemptStatus.PENDING
                || !attempt.getExpiresAt().isAfter(Instant.now())) {
            if (attempt.getStatus() == RewardAttemptStatus.PENDING) {
                attempt.setStatus(RewardAttemptStatus.EXPIRED);
            }
            return false;
        }
        RewardAttempt transactionOwner = attempts.findByProviderTransactionId(transactionId).orElse(null);
        if (transactionOwner != null && !transactionOwner.getId().equals(attempt.getId())) return false;

        Instant now = Instant.now();
        attempt.setProviderTransactionId(transactionId);
        attempt.setGrantedAt(now);
        attempt.setStatus(RewardAttemptStatus.GRANTED);
        attempts.saveAndFlush(attempt);
        entitlements.grantEpisodeUnlock(attempt.getUser(), attempt.getEpisode());
        return true;
    }

    private IntegrationConfigService.EffectiveConfig requireEnabled(RewardPlatform platform) {
        IntegrationConfigService.EffectiveConfig configured = enabledPlatform(platform);
        if (configured == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "该平台激励广告暂不可用");
        }
        return configured;
    }

    private IntegrationConfigService.EffectiveConfig enabledPlatform(RewardPlatform platform) {
        if (platform == null) return null;
        IntegrationConfigService.EffectiveConfig configured = platformConfig(platform);
        return configured.enabled() && integrations.isConfigured(configured) ? configured : null;
    }

    private IntegrationConfigService.EffectiveConfig platformConfig(RewardPlatform platform) {
        return integrations.effective(platform == RewardPlatform.ANDROID
                ? IntegrationProvider.CSJ_ANDROID : IntegrationProvider.CSJ_IOS);
    }

    private Duration validTtl() {
        Duration ttl = properties.getAttemptTtl();
        return ttl != null && !ttl.isZero() && !ttl.isNegative() ? ttl : Duration.ofMinutes(10);
    }

    private AttemptStatusResponse statusResponse(RewardAttempt attempt) {
        return new AttemptStatusResponse(attempt.getId(), attempt.getStatus(), attempt.getPlatform(),
                attempt.getEpisode().getId(), attempt.getExpiresAt(), attempt.getGrantedAt());
    }

    private boolean validCallbackShape(String userId, String transactionId, String rewardName,
                                       String rewardAmount, String extra, String sign) {
        return StringUtils.hasText(userId) && userId.length() <= 64
                && StringUtils.hasText(transactionId) && transactionId.length() <= 128
                && StringUtils.hasText(rewardName) && rewardName.length() <= 64
                && StringUtils.hasText(rewardAmount) && rewardAmount.length() <= 10
                && StringUtils.hasText(extra) && extra.length() <= 64
                && StringUtils.hasText(sign) && sign.length() == 64;
    }

    private static String randomNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String signature(String securityKey, String transactionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((securityKey + ":" + transactionId).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
