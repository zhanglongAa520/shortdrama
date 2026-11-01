package com.example.drama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final ObjectMapper objectMapper;
    private final String configuredMasterKey;
    private final Path masterKeyFile;
    private volatile SecretKey cachedKey;

    public IntegrationCryptoService(ObjectMapper objectMapper,
            @Value("${integration.master-key:}") String configuredMasterKey,
            @Value("${integration.master-key-file:./data/integrations.key}") String masterKeyFile) {
        this.objectMapper = objectMapper;
        this.configuredMasterKey = configuredMasterKey;
        this.masterKeyFile = Path.of(masterKeyFile).toAbsolutePath().normalize();
    }

    public String encrypt(IntegrationProvider provider, Map<String, String> values) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(true), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(values));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("第三方配置加密失败", exception);
        }
    }

    public Map<String, String> decrypt(IntegrationProvider provider, String encrypted) {
        try {
            if (encrypted == null || !encrypted.startsWith("v1:")) {
                throw new IllegalStateException("不支持的第三方配置密文版本");
            }
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
            if (payload.length <= NONCE_LENGTH) throw new IllegalStateException("第三方配置密文无效");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(false), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
            Map<String, String> values = objectMapper.readValue(cipher.doFinal(ciphertext), STRING_MAP);
            return new LinkedHashMap<>(values);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("第三方配置解密失败，请检查主密钥是否与数据库匹配", exception);
        }
    }

    public Path masterKeyFile() { return masterKeyFile; }

    private SecretKey masterKey(boolean createIfMissing) {
        SecretKey current = cachedKey;
        if (current != null) return current;
        synchronized (this) {
            if (cachedKey == null) cachedKey = new SecretKeySpec(loadKeyBytes(createIfMissing), "AES");
            return cachedKey;
        }
    }

    private byte[] loadKeyBytes(boolean createIfMissing) {
        if (StringUtils.hasText(configuredMasterKey)) return decodeKey(configuredMasterKey.trim());
        try {
            if (Files.exists(masterKeyFile, LinkOption.NOFOLLOW_LINKS)) return readKeyFile();
            if (!createIfMissing) {
                throw new IllegalStateException("数据库存在加密配置，但第三方配置主密钥文件不存在；请从备份恢复密钥");
            }
            Path parent = masterKeyFile.getParent();
            if (parent == null) throw new IllegalStateException("主密钥文件必须有父目录");
            Files.createDirectories(parent);
            byte[] generated = new byte[32];
            RANDOM.nextBytes(generated);
            try {
                publishNewKeyFile(parent, generated);
                return generated;
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                return readKeyFile();
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取或创建第三方配置主密钥文件", exception);
        }
    }

    private void publishNewKeyFile(Path parent, byte[] key) throws Exception {
        FileAttribute<Set<PosixFilePermission>> permissions =
                PosixFilePermissions.asFileAttribute(OWNER_ONLY);
        Path temporary;
        try {
            temporary = Files.createTempFile(parent, ".integration-master-", ".tmp", permissions);
        } catch (UnsupportedOperationException exception) {
            temporary = Files.createTempFile(parent, ".integration-master-", ".tmp");
            applyOwnerOnlyPermissions(temporary);
        }
        try {
            Files.writeString(temporary, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    temporary, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.createLink(masterKeyFile, temporary);
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                throw exception;
            } catch (UnsupportedOperationException | java.io.IOException linkException) {
                if (Files.exists(masterKeyFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw new java.nio.file.FileAlreadyExistsException(masterKeyFile.toString());
                }
                Files.move(temporary, masterKeyFile);
            }
            applyOwnerOnlyPermissions(masterKeyFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] readKeyFile() throws Exception {
        if (Files.isSymbolicLink(masterKeyFile)
                || !Files.isRegularFile(masterKeyFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("第三方配置主密钥路径必须是普通文件且不能是符号链接");
        }
        applyOwnerOnlyPermissions(masterKeyFile);
        return decodeKey(Files.readString(masterKeyFile, StandardCharsets.US_ASCII).trim());
    }

    private byte[] decodeKey(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("INTEGRATION_MASTER_KEY 必须是 Base64 编码的 32 字节密钥");
        }
    }

    private void applyOwnerOnlyPermissions(Path path) throws Exception {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX platforms rely on the filesystem's owner ACL.
        }
    }
}
