package com.madn.dropzone.service;

import com.madn.dropzone.entity.PhysicalStorageObject;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.enums.StorageObjectStatus;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.PhysicalStorageObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StorageGarbageCollectionService {

    private static final Logger log = LoggerFactory.getLogger(StorageGarbageCollectionService.class);

    private final PhysicalStorageObjectRepository physicalStorageObjectRepository;
    private final FileRepository fileRepository;
    private final StorageService storageService;

    public StorageGarbageCollectionService(
            PhysicalStorageObjectRepository physicalStorageObjectRepository,
            FileRepository fileRepository,
            StorageService storageService) {
        this.physicalStorageObjectRepository = physicalStorageObjectRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
    }

    /**
     * Executes scheduled garbage collection. Runs every 15 minutes by default.
     */
    @Scheduled(cron = "${dropzone.gc.cron:0 */15 * * * *}")
    public void scheduledGarbageCollection() {
        log.info("Starting scheduled storage garbage collection...");
        int purged = runGarbageCollection();
        log.info("Scheduled storage garbage collection finished. Purged objects: {}", purged);
    }

    /**
     * On-demand / manual garbage collection execution.
     * Identifies unreferenced physical storage objects, verifies safety invariants,
     * physically removes files from storage backend, and updates object status.
     *
     * @return count of physically purged storage objects
     */
    @Transactional
    public int runGarbageCollection() {
        if (physicalStorageObjectRepository == null) {
            return 0;
        }

        List<PhysicalStorageObject> eligibleObjects = physicalStorageObjectRepository
                .findByStatusAndReferenceCount(StorageObjectStatus.GC_ELIGIBLE, 0);

        int purgedCount = 0;

        for (PhysicalStorageObject pso : eligibleObjects) {
            try {
                // Safety invariant 1: Ensure no active or bin files reference this checksum
                long liveReferences = fileRepository.countFilesWithStatuses(
                        pso.getChecksum(),
                        List.of(FileStatus.ACTIVE, FileStatus.DELETED)
                );

                if (liveReferences > 0) {
                    log.warn("Storage object '{}' was marked GC_ELIGIBLE but has {} live references. Re-activating.",
                            pso.getChecksum(), liveReferences);
                    pso.setStatus(StorageObjectStatus.ACTIVE);
                    pso.setReferenceCount((int) liveReferences);
                    pso.setGcEligibleAt(null);
                    physicalStorageObjectRepository.save(pso);
                    continue;
                }

                // Safety invariant 2: Re-verify reference count is zero and status is GC_ELIGIBLE
                if (pso.getReferenceCount() != 0 || pso.getStatus() != StorageObjectStatus.GC_ELIGIBLE) {
                    log.info("Skipping storage object '{}': reference count or status changed concurrently", pso.getChecksum());
                    continue;
                }

                // Physically delete the file from the storage backend
                storageService.deleteFromStorage(pso.getStorageKey());

                // Update database to reflect successful purge
                pso.setStatus(StorageObjectStatus.PURGED);
                pso.setLastVerifiedAt(LocalDateTime.now());
                pso.setLastError(null);
                physicalStorageObjectRepository.save(pso);
                purgedCount++;

                log.info("Successfully garbage collected storage object: checksum={}, key={}",
                        pso.getChecksum(), pso.getStorageKey());

            } catch (Exception e) {
                // Record failure and keep eligible for safe retries
                pso.setFailureCount(pso.getFailureCount() + 1);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                pso.setLastError(msg.substring(0, Math.min(msg.length(), 950)));
                physicalStorageObjectRepository.save(pso);

                log.error("Failed to physically delete storage object: checksum={}, key={}, failureCount={}",
                        pso.getChecksum(), pso.getStorageKey(), pso.getFailureCount(), e);
            }
        }

        // Clean orphaned staging files older than 1 hour
        try {
            int stagingCleaned = storageService.cleanOrphanedStagingFiles(Duration.ofHours(1));
            if (stagingCleaned > 0) {
                log.info("Cleaned {} orphaned temporary staging files", stagingCleaned);
            }
        } catch (Exception e) {
            log.warn("Error while cleaning orphaned staging files", e);
        }

        return purgedCount;
    }
}
