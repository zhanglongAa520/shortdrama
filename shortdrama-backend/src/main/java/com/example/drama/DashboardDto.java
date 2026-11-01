package com.example.drama;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDto {
    private DashboardDto() {}

    public record Totals(long users, long activeMembers, long dramas, long publishedDramas, long episodes) {}
    public record Summary(long newUsers, long plays, long viewers, long paidOrders, long revenueCents,
                          long rewardAttempts, long rewardGranted, long comments, long danmaku) {}
    public record Daily(LocalDate date, long newUsers, long plays, long viewers, long paidOrders,
                        long revenueCents, long rewardAttempts, long rewardGranted) {}
    public record PaymentChannel(PaymentProvider provider, long paidOrders, long revenueCents) {}
    public record AdPlatform(RewardPlatform platform, long attempts, long granted) {}
    public record TopDrama(Long id, String title, String genre, long plays, long viewers, long favorites) {}
    public record DashboardResponse(LocalDate from, LocalDate to, String timezone, Instant generatedAt,
                                    Totals totals, Summary summary, List<Daily> daily,
                                    List<PaymentChannel> paymentChannels, List<AdPlatform> adPlatforms,
                                    List<TopDrama> topDramas) {}
}
