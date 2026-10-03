package com.fashionstore.catalog.service.impl;
import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.MediaFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarMediaReconciliationJob {
    private final MediaFileRepository repository;
    private final MediaFileService mediaFileService;
    private final MinioProperties minioProperties;
    private String afterId = "";
    @Scheduled(fixedDelayString = "${app.avatar.reconciliation-delay-ms:60000}")
    public void runReconciliation() {
        var now = LocalDateTime.now();
        // IDs only: each worker reloads and locks fresh rows in its own transaction.
        var ids = repository.findReconciliationIds(now.minusMinutes(1), now.minusHours(1),
                now.minusSeconds(2L * minioProperties.presignExpirySeconds()), afterId, PageRequest.of(0, 100));
        afterId = ids.size() == 100 ? ids.getLast() : "";
        for (String id : ids) {
            try { mediaFileService.reconcileMedia(id); }
            catch (Exception e) { log.warn("Media reconciliation failed for {}: {}", id, e.getMessage()); }
        }
    }
}
