package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "payment_order", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_order_provider_trade", columnNames = {"provider", "provider_trade_no"}))
public class PaymentOrder {
    @Id @Column(name = "order_no", length = 64)
    private String orderNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Enumerated(EnumType.STRING) @Column(name = "product_code", nullable = false, length = 32)
    private PaymentProduct productCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PaymentProvider provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PaymentStatus status;
    @Column(name = "amount_fen", nullable = false)
    private long amountFen;
    @Column(name = "membership_days")
    private Integer membershipDays;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "episode_id")
    private Episode episode;
    @Column(name = "provider_trade_no", length = 128)
    private String providerTradeNo;
    @Column(name = "return_url", length = 1000)
    private String returnUrl;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    protected PaymentOrder() {}
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public PaymentProduct getProductCode() { return productCode; }
    public void setProductCode(PaymentProduct productCode) { this.productCode = productCode; }
    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public long getAmountFen() { return amountFen; }
    public void setAmountFen(long amountFen) { this.amountFen = amountFen; }
    public Integer getMembershipDays() { return membershipDays; }
    public void setMembershipDays(Integer membershipDays) { this.membershipDays = membershipDays; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public String getProviderTradeNo() { return providerTradeNo; }
    public void setProviderTradeNo(String providerTradeNo) { this.providerTradeNo = providerTradeNo; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
