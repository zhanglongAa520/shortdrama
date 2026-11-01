package com.example.drama;

import com.example.drama.DashboardDto.AdPlatform;
import com.example.drama.DashboardDto.Daily;
import com.example.drama.DashboardDto.DashboardResponse;
import com.example.drama.DashboardDto.PaymentChannel;
import com.example.drama.DashboardDto.Summary;
import com.example.drama.DashboardDto.TopDrama;
import com.example.drama.DashboardDto.Totals;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DAYS = 93;

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DashboardResponse dashboard(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(6) : requestedFrom;
        validate(from, to, today);

        Instant generatedAt = Instant.now();
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", LocalDateTime.ofInstant(start, ZoneOffset.UTC))
                .addValue("end", LocalDateTime.ofInstant(end, ZoneOffset.UTC))
                .addValue("now", LocalDateTime.ofInstant(generatedAt, ZoneOffset.UTC));
        return new DashboardResponse(from, to, ZONE.getId(), generatedAt,
                totals(params), summary(params), daily(from, to, params),
                paymentChannels(params), adPlatforms(params), topDramas(params));
    }

    private Totals totals(MapSqlParameterSource params) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM app_user u WHERE u.role = 'USER') AS users,
                  (SELECT COUNT(*) FROM app_user u WHERE u.role = 'USER'
                    AND u.membership_expires_at > :now) AS active_members,
                  (SELECT COUNT(*) FROM drama) AS dramas,
                  (SELECT COUNT(*) FROM drama d WHERE d.status = 'PUBLISHED') AS published_dramas,
                  (SELECT COUNT(*) FROM episode) AS episodes
                """;
        return jdbc.queryForObject(sql, params, (rs, row) -> new Totals(
                rs.getLong("users"), rs.getLong("active_members"), rs.getLong("dramas"),
                rs.getLong("published_dramas"), rs.getLong("episodes")));
    }

    private Summary summary(MapSqlParameterSource params) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM app_user u WHERE u.role = 'USER'
                    AND u.created_at >= :start AND u.created_at < :end) AS new_users,
                  (SELECT COUNT(*) FROM playback_event p JOIN app_user u ON u.id = p.user_id
                    WHERE u.role = 'USER' AND p.started_at >= :start AND p.started_at < :end) AS plays,
                  (SELECT COUNT(DISTINCT p.user_id) FROM playback_event p JOIN app_user u ON u.id = p.user_id
                    WHERE u.role = 'USER' AND p.started_at >= :start AND p.started_at < :end) AS viewers,
                  (SELECT COUNT(*) FROM payment_order p JOIN app_user u ON u.id = p.user_id
                    WHERE u.role = 'USER' AND p.status = 'PAID'
                    AND p.paid_at >= :start AND p.paid_at < :end) AS paid_orders,
                  (SELECT COALESCE(SUM(p.amount_fen), 0) FROM payment_order p JOIN app_user u ON u.id = p.user_id
                    WHERE u.role = 'USER' AND p.status = 'PAID'
                    AND p.paid_at >= :start AND p.paid_at < :end) AS revenue_cents,
                  (SELECT COUNT(*) FROM reward_attempt r JOIN app_user u ON u.id = r.user_id
                    WHERE u.role = 'USER' AND r.created_at >= :start AND r.created_at < :end) AS reward_attempts,
                  (SELECT COUNT(*) FROM reward_attempt r JOIN app_user u ON u.id = r.user_id
                    WHERE u.role = 'USER' AND r.granted_at >= :start AND r.granted_at < :end) AS reward_granted,
                  (SELECT COUNT(*) FROM interaction i JOIN app_user u ON u.id = i.user_id
                    WHERE u.role = 'USER' AND i.type = 'COMMENT'
                    AND i.created_at >= :start AND i.created_at < :end) AS comments,
                  (SELECT COUNT(*) FROM interaction i JOIN app_user u ON u.id = i.user_id
                    WHERE u.role = 'USER' AND i.type = 'DANMAKU'
                    AND i.created_at >= :start AND i.created_at < :end) AS danmaku
                """;
        return jdbc.queryForObject(sql, params, (rs, row) -> new Summary(
                rs.getLong("new_users"), rs.getLong("plays"), rs.getLong("viewers"),
                rs.getLong("paid_orders"), rs.getLong("revenue_cents"),
                rs.getLong("reward_attempts"), rs.getLong("reward_granted"),
                rs.getLong("comments"), rs.getLong("danmaku")));
    }

    private List<Daily> daily(LocalDate from, LocalDate to, MapSqlParameterSource params) {
        Map<LocalDate, DailyAccumulator> days = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, new DailyAccumulator());
        }
        groupedCount("app_user", "created_at", "role = 'USER'", days, params,
                (day, count) -> days.get(day).newUsers = count);
        String playbackDay = dayCase("p.started_at", days, params);
        jdbc.query("""
                SELECT %s AS metric_date, COUNT(*) AS plays,
                       COUNT(DISTINCT p.user_id) AS viewers
                FROM playback_event p JOIN app_user u ON u.id = p.user_id
                WHERE u.role = 'USER' AND p.started_at >= :start AND p.started_at < :end
                GROUP BY metric_date
                """.formatted(playbackDay), params, (RowCallbackHandler) rs -> {
            DailyAccumulator value = days.get(LocalDate.parse(rs.getString("metric_date")));
            if (value != null) {
                value.plays = rs.getLong("plays");
                value.viewers = rs.getLong("viewers");
            }
        });
        String paymentDay = dayCase("p.paid_at", days, params);
        jdbc.query("""
                SELECT %s AS metric_date, COUNT(*) AS paid_orders,
                       COALESCE(SUM(p.amount_fen), 0) AS revenue_cents
                FROM payment_order p JOIN app_user u ON u.id = p.user_id
                WHERE u.role = 'USER' AND p.status = 'PAID'
                  AND p.paid_at >= :start AND p.paid_at < :end
                GROUP BY metric_date
                """.formatted(paymentDay), params, (RowCallbackHandler) rs -> {
            DailyAccumulator value = days.get(LocalDate.parse(rs.getString("metric_date")));
            if (value != null) {
                value.paidOrders = rs.getLong("paid_orders");
                value.revenueCents = rs.getLong("revenue_cents");
            }
        });
        groupedRewardCount("created_at", "1 = 1", days, params,
                (day, count) -> days.get(day).rewardAttempts = count);
        groupedRewardCount("granted_at", "r.granted_at IS NOT NULL", days, params,
                (day, count) -> days.get(day).rewardGranted = count);
        return days.entrySet().stream().map(entry -> entry.getValue().response(entry.getKey())).toList();
    }

    private List<PaymentChannel> paymentChannels(MapSqlParameterSource params) {
        Map<PaymentProvider, long[]> values = new EnumMap<>(PaymentProvider.class);
        for (PaymentProvider provider : PaymentProvider.values()) values.put(provider, new long[2]);
        jdbc.query("""
                SELECT p.provider, COUNT(*) AS paid_orders, COALESCE(SUM(p.amount_fen), 0) AS revenue_cents
                FROM payment_order p JOIN app_user u ON u.id = p.user_id
                WHERE u.role = 'USER' AND p.status = 'PAID'
                  AND p.paid_at >= :start AND p.paid_at < :end
                GROUP BY p.provider
                """, params, (RowCallbackHandler) rs -> values.put(PaymentProvider.valueOf(rs.getString("provider")),
                new long[]{rs.getLong("paid_orders"), rs.getLong("revenue_cents")}));
        return java.util.Arrays.stream(PaymentProvider.values())
                .map(provider -> new PaymentChannel(provider, values.get(provider)[0], values.get(provider)[1]))
                .toList();
    }

    private List<AdPlatform> adPlatforms(MapSqlParameterSource params) {
        Map<RewardPlatform, long[]> values = new EnumMap<>(RewardPlatform.class);
        for (RewardPlatform platform : RewardPlatform.values()) values.put(platform, new long[2]);
        jdbc.query("""
                SELECT metrics.platform, SUM(metrics.attempts) AS attempts, SUM(metrics.granted) AS granted
                FROM (
                  SELECT r.platform, COUNT(*) AS attempts, 0 AS granted
                  FROM reward_attempt r JOIN app_user u ON u.id = r.user_id
                  WHERE u.role = 'USER' AND r.created_at >= :start AND r.created_at < :end
                  GROUP BY r.platform
                  UNION ALL
                  SELECT r.platform, 0 AS attempts, COUNT(*) AS granted
                  FROM reward_attempt r JOIN app_user u ON u.id = r.user_id
                  WHERE u.role = 'USER' AND r.granted_at >= :start AND r.granted_at < :end
                  GROUP BY r.platform
                ) metrics
                GROUP BY metrics.platform
                """, params, (RowCallbackHandler) rs -> values.put(RewardPlatform.valueOf(rs.getString("platform")),
                new long[]{rs.getLong("attempts"), rs.getLong("granted")}));
        return java.util.Arrays.stream(RewardPlatform.values())
                .map(platform -> new AdPlatform(platform, values.get(platform)[0], values.get(platform)[1]))
                .toList();
    }

    private List<TopDrama> topDramas(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT d.id, d.title, d.genre, COUNT(p.id) AS plays,
                       COUNT(DISTINCT p.user_id) AS viewers,
                       (SELECT COUNT(*) FROM favorite f JOIN app_user fu ON fu.id = f.user_id
                         WHERE f.drama_id = d.id AND fu.role = 'USER') AS favorites
                FROM playback_event p
                JOIN app_user u ON u.id = p.user_id
                JOIN drama d ON d.id = p.drama_id
                WHERE u.role = 'USER' AND p.started_at >= :start AND p.started_at < :end
                GROUP BY d.id, d.title, d.genre
                ORDER BY plays DESC, viewers DESC, d.id ASC
                LIMIT 10
                """, params, (rs, row) -> new TopDrama(rs.getLong("id"), rs.getString("title"),
                rs.getString("genre"), rs.getLong("plays"), rs.getLong("viewers"),
                rs.getLong("favorites")));
    }

    private void groupedCount(String table, String timestampColumn, String extraCondition,
                              Map<LocalDate, DailyAccumulator> days, MapSqlParameterSource params,
                              DailyCountConsumer consumer) {
        String sql = "SELECT " + dayCase(timestampColumn, days, params)
                + " AS metric_date, COUNT(*) AS metric_count "
                + "FROM " + table + " WHERE " + extraCondition + " AND " + timestampColumn
                + " >= :start AND " + timestampColumn + " < :end GROUP BY metric_date";
        jdbc.query(sql, params, (RowCallbackHandler) rs -> consumer.accept(
                LocalDate.parse(rs.getString("metric_date")), rs.getLong("metric_count")));
    }

    private void groupedRewardCount(String timestampColumn, String extraCondition,
                                    Map<LocalDate, DailyAccumulator> days, MapSqlParameterSource params,
                                    DailyCountConsumer consumer) {
        String qualified = "r." + timestampColumn;
        String sql = "SELECT " + dayCase(qualified, days, params)
                + " AS metric_date, COUNT(*) AS metric_count "
                + "FROM reward_attempt r JOIN app_user u ON u.id = r.user_id "
                + "WHERE u.role = 'USER' AND " + extraCondition + " AND r." + timestampColumn
                + " >= :start AND r." + timestampColumn + " < :end GROUP BY metric_date";
        jdbc.query(sql, params, (RowCallbackHandler) rs -> consumer.accept(
                LocalDate.parse(rs.getString("metric_date")), rs.getLong("metric_count")));
    }

    private String dayCase(String timestampColumn, Map<LocalDate, DailyAccumulator> days,
                           MapSqlParameterSource params) {
        StringBuilder expression = new StringBuilder("CASE");
        int index = 0;
        for (LocalDate day : days.keySet()) {
            String startName = "dailyBoundary" + index;
            String endName = "dailyBoundary" + (index + 1);
            params.addValue(startName, utcBoundary(day));
            params.addValue(endName, utcBoundary(day.plusDays(1)));
            expression.append(" WHEN ").append(timestampColumn).append(" >= :").append(startName)
                    .append(" AND ").append(timestampColumn).append(" < :").append(endName)
                    .append(" THEN '").append(day).append("'");
            index++;
        }
        return expression.append(" END").toString();
    }

    private LocalDateTime utcBoundary(LocalDate day) {
        return LocalDateTime.ofInstant(day.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC);
    }

    private void validate(LocalDate from, LocalDate to, LocalDate today) {
        if (from.isAfter(today) || to.isAfter(today)) badRequest("日期不能晚于今天");
        if (from.isAfter(to)) badRequest("from 不能晚于 to");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) badRequest("日期范围最多 93 天");
    }

    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    @FunctionalInterface
    private interface DailyCountConsumer { void accept(LocalDate date, long count); }

    private static final class DailyAccumulator {
        long newUsers;
        long plays;
        long viewers;
        long paidOrders;
        long revenueCents;
        long rewardAttempts;
        long rewardGranted;

        Daily response(LocalDate date) {
            return new Daily(date, newUsers, plays, viewers, paidOrders, revenueCents,
                    rewardAttempts, rewardGranted);
        }
    }
}
