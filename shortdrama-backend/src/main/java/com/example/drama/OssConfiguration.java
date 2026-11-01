package com.example.drama;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {
    @Bean(destroyMethod = "close")
    @org.springframework.context.annotation.Conditional(OssConfigured.class)
    OSSClient ossClient(OssProperties properties) {
        properties.validate();
        return OSSClient.newBuilder()
                .region(properties.getRegion())
                .endpoint(properties.endpointUrl())
                .connectTimeout(Duration.ofSeconds(15))
                .readWriteTimeout(Duration.ofMinutes(5))
                .credentialsProvider(new StaticCredentialsProvider(
                        properties.getAccessKeyId(), properties.getAccessKeySecret(),
                        StringUtils.hasText(properties.getSecurityToken())
                                ? properties.getSecurityToken() : null))
                .build();
    }

    static final class OssConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            org.springframework.core.env.Environment environment = context.getEnvironment();
            return StringUtils.hasText(environment.getProperty("oss.access-key-id"))
                    || StringUtils.hasText(environment.getProperty("oss.access-key-secret"))
                    || StringUtils.hasText(environment.getProperty("oss.security-token"))
                    || StringUtils.hasText(environment.getProperty("oss.bucket-name"))
                    || StringUtils.hasText(environment.getProperty("oss.endpoint"));
        }
    }
}
