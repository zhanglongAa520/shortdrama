package com.example.drama;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PaymentDto {
    private PaymentDto() {}
    public record ProviderConfigResponse(boolean configured) {}
    public record MembershipProduct(long priceCents, int days, String title) {}
    public record EpisodeProduct(long priceCents) {}
    public record ConfigResponse(Map<PaymentProvider, ProviderConfigResponse> providers,
                                 MembershipProduct membership, EpisodeProduct episode) {}
    public record ProductResponse(PaymentProduct productType, String title, long priceCents, Integer days) {}
    public record CreateOrderRequest(@NotNull PaymentProduct productType, @NotNull PaymentProvider provider,
                                     Long episodeId, @Size(max = 1000) String returnUrl) {}
    public record OrderResponse(String id, PaymentStatus status, PaymentProvider provider,
                                PaymentProduct productType, long priceCents, Long episodeId, String paymentUrl,
                                String html, Instant createdAt, Instant paidAt) {}
}
