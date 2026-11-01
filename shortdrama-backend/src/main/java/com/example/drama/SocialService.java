package com.example.drama;

import com.example.drama.SocialDto.Response;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SocialService {
    private static final String STATE_SQL = """
            SELECT d.id AS drama_id,
                   CASE WHEN EXISTS (SELECT 1 FROM drama_like dl
                     WHERE dl.drama_id = d.id AND dl.user_id = :userId) THEN 1 ELSE 0 END AS liked,
                   CASE WHEN EXISTS (SELECT 1 FROM favorite f
                     WHERE f.drama_id = d.id AND f.user_id = :userId) THEN 1 ELSE 0 END AS favorited
            FROM drama d
            WHERE d.id = :dramaId AND d.status = 'PUBLISHED'
            """;

    private final AppUserRepository users;
    private final DramaRepository dramas;
    private final DramaLikeRepository likes;
    private final NamedParameterJdbcTemplate jdbc;
    private final SocialCountCache cache;
    private final SocialCountDatabaseReader countReader;

    public SocialService(AppUserRepository users, DramaRepository dramas, DramaLikeRepository likes,
                         NamedParameterJdbcTemplate jdbc, SocialCountCache cache,
                         SocialCountDatabaseReader countReader) {
        this.users = users;
        this.dramas = dramas;
        this.likes = likes;
        this.jdbc = jdbc;
        this.cache = cache;
        this.countReader = countReader;
    }

    @Transactional(readOnly = true)
    public Response social(String username, Long dramaId) {
        return response(user(username).getId(), dramaId, true);
    }

    @Transactional
    public Response like(String username, Long dramaId) {
        AppUser user = lockedUser(username);
        Drama drama = publishedDrama(dramaId);
        if (!likes.existsByUserIdAndDramaId(user.getId(), dramaId)) {
            DramaLike like = new DramaLike();
            like.setUser(user);
            like.setDrama(drama);
            likes.saveAndFlush(like);
            cache.invalidateAfterCommit(dramaId);
        }
        return response(user.getId(), dramaId, false);
    }

    @Transactional
    public Response unlike(String username, Long dramaId) {
        AppUser user = lockedUser(username);
        publishedDrama(dramaId);
        if (likes.deleteByUserIdAndDramaId(user.getId(), dramaId) > 0) {
            likes.flush();
            cache.invalidateAfterCommit(dramaId);
        }
        return response(user.getId(), dramaId, false);
    }

    private Response response(Long userId, Long dramaId, boolean useCache) {
        var rows = jdbc.query(STATE_SQL, Map.of("userId", userId, "dramaId", dramaId),
                (rs, row) -> new SocialState(rs.getLong("drama_id"),
                        rs.getBoolean("liked"), rs.getBoolean("favorited")));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在");
        }
        SocialState state = rows.getFirst();
        SocialCounts counts = useCache
                ? cache.getOrLoad(dramaId, () -> countReader.loadCommitted(dramaId))
                : countReader.loadCurrentTransaction(dramaId);
        return new Response(state.dramaId(), counts.likeCount(), counts.commentCount(),
                counts.favoriteCount(), state.liked(), state.favorited());
    }

    private AppUser user(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AppUser lockedUser(String username) {
        AppUser found = user(username);
        return users.findForUpdate(found.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Drama publishedDrama(Long dramaId) {
        return dramas.findByIdAndStatus(dramaId, DramaStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
    }

    private record SocialState(Long dramaId, boolean liked, boolean favorited) {}
}
