package com.example.drama;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String securityToken = "";
    private String bucketName = "";
    private String region = "cn-hangzhou";
    private String endpoint = "";

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getSecurityToken() { return securityToken; }
    public void setSecurityToken(String securityToken) { this.securityToken = securityToken; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    String endpointUrl() {
        String value = StringUtils.hasText(endpoint) ? endpoint.trim() : "oss-" + region + ".aliyuncs.com";
        URI uri = URI.create(value.contains("://") ? value : "https://" + value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalStateException("OSS endpoint 必须是 HTTPS 服务端点");
        }
        return "https://" + uri.getRawAuthority();
    }

    void validate() {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)
                || !StringUtils.hasText(bucketName)
                || !bucketName.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || !region.matches("[a-z0-9-]+")) {
            throw new IllegalStateException("OSS access key、bucket 和 region 配置不完整");
        }
        endpointUrl();
    }

    boolean configured() {
        try {
            validate();
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return false;
        }
    }
}
