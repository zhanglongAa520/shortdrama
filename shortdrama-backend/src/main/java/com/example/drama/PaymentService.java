package com.example.drama;

import com.example.drama.PaymentDto.ConfigResponse;
import com.example.drama.PaymentDto.CreateOrderRequest;
import com.example.drama.PaymentDto.EpisodeProduct;
import com.example.drama.PaymentDto.MembershipProduct;
import com.example.drama.PaymentDto.OrderResponse;
import com.example.drama.PaymentDto.ProviderConfigResponse;
import com.example.drama.PaymentGatewayService.GatewayResult;
import com.example.drama.PaymentGatewayService.VerifiedPayment;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class PaymentService {
    private final long membershipPriceCents;
    private final int membershipDays;
    private final long episodePriceCents;
    private final PaymentOrderRepository orders;
    private final AppUserRepository users;
    private final EpisodeRepository episodes;
    private final EpisodeEntitlementService entitlements;
    private final PaymentGatewayService gateway;
    private final Set<String> allowedReturnOrigins;

    public PaymentService(PaymentOrderRepository orders, AppUserRepository users, EpisodeRepository episodes,
                          EpisodeEntitlementService entitlements, PaymentGatewayService gateway,
                          @Value("${payment.membership.price-cents:1990}") long membershipPriceCents,
                          @Value("${payment.membership.days:30}") int membershipDays,
                          @Value("${payment.episode.price-cents:290}") long episodePriceCents,
                          @Value("${payment.allowed-return-origins:http://localhost:5173,http://localhost:5174}")
                          String allowedReturnOrigins) {
        this.orders = orders;
        this.users = users;
        this.episodes = episodes;
        this.entitlements = entitlements;
        this.gateway = gateway;
        if (membershipPriceCents <= 0 || episodePriceCents <= 0 || membershipDays <= 0) {
            throw new IllegalArgumentException("支付价格和会员天数必须为正数");
        }
        this.membershipPriceCents = membershipPriceCents;
        this.membershipDays = membershipDays;
        this.episodePriceCents = episodePriceCents;
        this.allowedReturnOrigins = Arrays.stream(allowedReturnOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.replaceAll("/+$", "").toLowerCase()).collect(Collectors.toUnmodifiableSet());
    }

    public ConfigResponse config() {
        Map<PaymentProvider, ProviderConfigResponse> providers = new LinkedHashMap<>();
        for (PaymentProvider provider : PaymentProvider.values()) {
            providers.put(provider, new ProviderConfigResponse(gateway.configured(provider)));
        }
        return new ConfigResponse(providers,
                new MembershipProduct(membershipPriceCents, membershipDays, "月度会员"),
                new EpisodeProduct(episodePriceCents));
    }

    public List<PaymentDto.ProductResponse> catalog() {
        return List.of(new PaymentDto.ProductResponse(PaymentProduct.MEMBERSHIP, "月度会员",
                        membershipPriceCents, membershipDays),
                new PaymentDto.ProductResponse(PaymentProduct.EPISODE, "单集解锁", episodePriceCents, null));
    }

    @Transactional
    public OrderResponse create(String username, CreateOrderRequest input, String clientIp) {
        if (!gateway.configured(input.provider())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "支付渠道尚未配置");
        }
        AppUser user = user(username);
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo("YK" + UUID.randomUUID().toString().replace("-", ""));
        order.setUser(user);
        order.setProductCode(input.productType());
        order.setProvider(input.provider());
        order.setStatus(PaymentStatus.CREATED);
        order.setReturnUrl(validateReturnUrl(input.returnUrl()));
        if (input.productType() == PaymentProduct.MEMBERSHIP) {
            if (input.episodeId() != null) badRequest("会员订单不能指定分集");
            order.setAmountFen(membershipPriceCents);
            order.setMembershipDays(membershipDays);
        } else {
            if (input.episodeId() == null) badRequest("单集订单必须指定 episodeId");
            Episode episode = episodes.findById(input.episodeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在"));
            if (episode.getDrama().getStatus() != DramaStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在");
            }
            if (episode.isFree()) badRequest("免费分集无需购买");
            order.setEpisode(episode);
            order.setAmountFen(episodePriceCents);
        }
        orders.saveAndFlush(order);
        GatewayResult result = gateway.create(order, clientIp);
        return response(order, result.paymentUrl(), result.html());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String username) {
        AppUser user = user(username);
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(order -> response(order, null, null)).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse find(String username, String orderNo) {
        PaymentOrder order = orders.findById(orderNo)
                .filter(item -> item.getUser().getUsername().equalsIgnoreCase(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return response(order, null, null);
    }

    @Transactional
    public void complete(PaymentProvider provider, VerifiedPayment verified) {
        if (verified.orderNo() == null || verified.providerTradeNo() == null) {
            badRequest("支付回调缺少订单信息");
        }
        PaymentOrder order = orders.findForUpdate(verified.orderNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getProvider() != provider || order.getAmountFen() != verified.amountFen()) {
            badRequest("支付回调与订单不匹配");
        }
        if (order.getStatus() == PaymentStatus.PAID) {
            if (!verified.providerTradeNo().equals(order.getProviderTradeNo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已由其他交易完成");
            }
            return;
        }
        if (order.getStatus() != PaymentStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态不可支付");
        }
        AppUser lockedUser = users.findForUpdate(order.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (order.getProductCode() == PaymentProduct.MEMBERSHIP) {
            Instant base = lockedUser.getMembershipExpiresAt() != null
                    && lockedUser.getMembershipExpiresAt().isAfter(Instant.now())
                    ? lockedUser.getMembershipExpiresAt() : Instant.now();
            lockedUser.setMembershipExpiresAt(base.plus(order.getMembershipDays(), ChronoUnit.DAYS));
        } else {
            if (order.getEpisode() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "订单对应分集已不存在");
            }
            entitlements.grantEpisodeUnlock(lockedUser, order.getEpisode());
        }
        order.setProviderTradeNo(verified.providerTradeNo());
        order.setPaidAt(Instant.now());
        order.setStatus(PaymentStatus.PAID);
    }

    private AppUser user(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String validateReturnUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) badRequest("returnUrl 必须是 HTTP(S) 绝对地址");
            String origin = uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            if (!allowedReturnOrigins.contains(origin)) badRequest("returnUrl 来源不在允许列表");
            return value;
        } catch (IllegalArgumentException exception) {
            badRequest("returnUrl 格式无效");
            return null;
        }
    }

    private OrderResponse response(PaymentOrder order, String paymentUrl, String html) {
        return new OrderResponse(order.getOrderNo(), order.getStatus(), order.getProvider(), order.getProductCode(),
                order.getAmountFen(), order.getEpisode() == null ? null : order.getEpisode().getId(),
                paymentUrl, html, order.getCreatedAt(), order.getPaidAt());
    }

    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
