package com.example.drama;

import com.example.drama.InteractionDto.CreateRequest;
import com.example.drama.InteractionDto.Response;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InteractionService {
    private final InteractionRepository interactions;
    private final DramaRepository dramas;
    private final EpisodeRepository episodes;
    private final AppUserRepository users;
    private final EpisodeEntitlementService entitlements;
    private final SocialCountCache socialCache;

    public InteractionService(InteractionRepository interactions, DramaRepository dramas,
                              EpisodeRepository episodes, AppUserRepository users,
                              EpisodeEntitlementService entitlements, SocialCountCache socialCache) {
        this.interactions = interactions;
        this.dramas = dramas;
        this.episodes = episodes;
        this.users = users;
        this.entitlements = entitlements;
        this.socialCache = socialCache;
    }

    @Transactional(readOnly = true)
    public List<Response> visible(Long dramaId, InteractionType type, Long episodeId, String username) {
        publishedDrama(dramaId);
        if (type == InteractionType.DANMAKU && episodeId == null) {
            badRequest("查询弹幕必须指定 episodeId");
        }
        if (episodeId != null) {
            Episode episode = requireEpisode(dramaId, episodeId);
            if (!entitlements.canWatch(username, episode)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前分集需要会员或单集解锁");
            }
        }
        List<Interaction> result = episodeId == null
                ? interactions.findByDramaIdAndTypeAndStatusOrderByCreatedAtAsc(dramaId, type, InteractionStatus.VISIBLE)
                : interactions.findByDramaIdAndTypeAndEpisodeIdAndStatusOrderByCreatedAtAsc(
                        dramaId, type, episodeId, InteractionStatus.VISIBLE);
        return result.stream().map(this::response).toList();
    }

    @Transactional
    public Response create(Long dramaId, CreateRequest input, String username) {
        Drama drama = publishedDrama(dramaId);
        String text = input.text().trim();
        if (input.type() == InteractionType.COMMENT) {
            if (text.length() > 500) badRequest("评论不能超过 500 字");
        } else {
            if (text.length() > 30) badRequest("弹幕不能超过 30 字");
            if (input.episodeId() == null || input.at() == null || input.lane() == null) {
                badRequest("弹幕必须包含 episodeId、at 和 lane");
            }
            if (!Double.isFinite(input.at()) || input.at() < 0) badRequest("弹幕时间必须是非负有限数");
            if (input.lane() < 0 || input.lane() > 2) badRequest("弹幕轨道必须为 0 到 2");
        }
        Episode episode = input.episodeId() == null ? null : requireEpisode(dramaId, input.episodeId());
        if (episode != null && !entitlements.canWatch(username, episode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前分集需要会员或单集解锁");
        }
        Interaction interaction = new Interaction();
        interaction.setType(input.type());
        interaction.setText(text);
        interaction.setDrama(drama);
        interaction.setEpisode(episode);
        interaction.setAt(input.type() == InteractionType.DANMAKU ? input.at() : null);
        interaction.setLane(input.type() == InteractionType.DANMAKU ? input.lane() : null);
        interaction.setStatus(InteractionStatus.VISIBLE);
        interaction.setUser(users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        Interaction saved = interactions.saveAndFlush(interaction);
        if (saved.getType() == InteractionType.COMMENT) {
            socialCache.invalidateAfterCommit(dramaId);
        }
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<Response> adminList(InteractionType type, InteractionStatus status) {
        Specification<Interaction> spec = Specification.where(null);
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return interactions.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::response).toList();
    }

    @Transactional
    public Response setStatus(Long id, InteractionStatus status) {
        Interaction interaction = find(id);
        InteractionStatus previous = interaction.getStatus();
        interaction.setStatus(status);
        if (interaction.getType() == InteractionType.COMMENT && previous != status) {
            socialCache.invalidateAfterCommit(interaction.getDrama().getId());
        }
        return response(interaction);
    }

    @Transactional
    public void delete(Long id) {
        Interaction interaction = find(id);
        interactions.delete(interaction);
        if (interaction.getType() == InteractionType.COMMENT
                && interaction.getStatus() == InteractionStatus.VISIBLE) {
            socialCache.invalidateAfterCommit(interaction.getDrama().getId());
        }
    }

    private Drama publishedDrama(Long id) {
        return dramas.findByIdAndStatus(id, DramaStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
    }

    private Episode requireEpisode(Long dramaId, Long episodeId) {
        Episode episode = episodes.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "分集不存在"));
        if (!episode.getDrama().getId().equals(dramaId)) badRequest("分集不属于当前短剧");
        return episode;
    }

    private Interaction find(Long id) {
        return interactions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "互动内容不存在"));
    }

    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private Response response(Interaction item) {
        return new Response(item.getId(), item.getType(), item.getText(), item.getDrama().getId(),
                item.getEpisode() == null ? null : item.getEpisode().getId(), item.getAt(), item.getLane(),
                item.getStatus(), item.getUser().getUsername(), item.getUser().getNickname(), item.getCreatedAt());
    }
}
