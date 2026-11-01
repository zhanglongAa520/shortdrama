package com.example.drama;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SocialCountDatabaseReader {
    private static final String COUNTS_SQL = """
            SELECT (SELECT COUNT(*) FROM drama_like dl WHERE dl.drama_id = d.id) AS like_count,
                   (SELECT COUNT(*) FROM interaction i
                     WHERE i.drama_id = d.id AND i.type = 'COMMENT' AND i.status = 'VISIBLE') AS comment_count,
                   (SELECT COUNT(*) FROM favorite f WHERE f.drama_id = d.id) AS favorite_count
            FROM drama d
            WHERE d.id = :dramaId AND d.status = 'PUBLISHED'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SocialCountDatabaseReader(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, readOnly = true)
    public SocialCounts loadCommitted(Long dramaId) { return query(dramaId); }

    public SocialCounts loadCurrentTransaction(Long dramaId) { return query(dramaId); }

    private SocialCounts query(Long dramaId) {
        var rows = jdbc.query(COUNTS_SQL, Map.of("dramaId", dramaId),
                (rs, row) -> new SocialCounts(rs.getLong("like_count"),
                        rs.getLong("comment_count"), rs.getLong("favorite_count")));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在");
        }
        return rows.getFirst();
    }
}
