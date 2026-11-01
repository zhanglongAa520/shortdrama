package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "integration_config")
public class IntegrationConfig {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private IntegrationProvider provider;
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    @Column(name = "encrypted_values", nullable = false, columnDefinition = "TEXT")
    private String encryptedValues;
    @Column(name = "config_version", nullable = false)
    private long configVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public IntegrationProvider getProvider() { return provider; }
    public void setProvider(IntegrationProvider provider) { this.provider = provider; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEncryptedValues() { return encryptedValues; }
    public void setEncryptedValues(String encryptedValues) { this.encryptedValues = encryptedValues; }
    public long getConfigVersion() { return configVersion; }
    public void setConfigVersion(long configVersion) { this.configVersion = configVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
