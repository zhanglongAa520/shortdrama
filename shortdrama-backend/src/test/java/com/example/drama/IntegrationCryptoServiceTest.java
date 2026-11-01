package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationCryptoServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void concurrentFirstWritersShareOneAtomicallyPublishedKey() throws Exception {
        Path keyFile = temporaryDirectory.resolve("concurrent.key");
        IntegrationCryptoService first = service(keyFile);
        IntegrationCryptoService second = service(keyFile);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var firstCiphertext = pool.submit(() -> {
                start.await();
                return first.encrypt(IntegrationProvider.WECHAT, Map.of("secret", "first"));
            });
            var secondCiphertext = pool.submit(() -> {
                start.await();
                return second.encrypt(IntegrationProvider.WECHAT, Map.of("secret", "second"));
            });
            start.countDown();
            String encryptedByFirst = firstCiphertext.get(5, TimeUnit.SECONDS);
            String encryptedBySecond = secondCiphertext.get(5, TimeUnit.SECONDS);
            assertEquals("first", second.decrypt(IntegrationProvider.WECHAT, encryptedByFirst).get("secret"));
            assertEquals("second", first.decrypt(IntegrationProvider.WECHAT, encryptedBySecond).get("secret"));
        }
    }

    @Test
    void missingKeyForExistingCiphertextFailsWithoutGeneratingReplacement() throws Exception {
        Path keyFile = temporaryDirectory.resolve("lost.key");
        IntegrationCryptoService writer = service(keyFile);
        String ciphertext = writer.encrypt(IntegrationProvider.ALIPAY, Map.of("secret", "value"));
        Files.delete(keyFile);

        IntegrationCryptoService recovery = service(keyFile);
        assertThrows(IllegalStateException.class,
                () -> recovery.decrypt(IntegrationProvider.ALIPAY, ciphertext));
        assertFalse(Files.exists(keyFile));
    }

    private IntegrationCryptoService service(Path keyFile) {
        return new IntegrationCryptoService(new ObjectMapper(), "", keyFile.toString());
    }
}
