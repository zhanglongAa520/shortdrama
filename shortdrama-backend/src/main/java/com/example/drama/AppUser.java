package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_username", columnNames = "username"))
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 64)
    private String nickname;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;
    @Column(name = "membership_expires_at")
    private Instant membershipExpiresAt;
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected AppUser() {}

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Instant getMembershipExpiresAt() { return membershipExpiresAt; }
    public void setMembershipExpiresAt(Instant membershipExpiresAt) { this.membershipExpiresAt = membershipExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
