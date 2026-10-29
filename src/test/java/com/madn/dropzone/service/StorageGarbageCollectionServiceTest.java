package com.madn.dropzone.service;

import com.madn.dropzone.entity.PhysicalStorageObject;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.enums.StorageObjectStatus;
import com.madn.dropzone.exception.storage.StorageException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.PhysicalStorageObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageGarbageCollectionServiceTest {

    @Mock
    private PhysicalStorageObjectRepository physicalStorageObjectRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageGarbageCollectionService garbageCollectionService;

    private PhysicalStorageObject eligibleObject;

    @BeforeEach
    void setUp() {
        eligibleObject = PhysicalStorageObject.builder()
                .checksum("sha256-unreferenced")
                .storageKey("storage-key-123")
                .physicalSize(4096L)
                .referenceCount(0)
                .status(StorageObjectStatus.GC_ELIGIBLE)
                .gcEligibleAt(LocalDateTime.now().minusMinutes(30))
                .failureCount(0)
                .build();
    }

    @Test
    @DisplayName("Successfully purge unreferenced physical storage object when reference count is zero")
    void runGarbageCollection_Success() {
        when(physicalStorageObjectRepository.findByStatusAndReferenceCount(StorageObjectStatus.GC_ELIGIBLE, 0))
                .thenReturn(List.of(eligibleObject));
        when(fileRepository.countFilesWithStatuses(eq("sha256-unreferenced"), anyList()))
                .thenReturn(0L);

        int purgedCount = garbageCollectionService.runGarbageCollection();

        assertThat(purgedCount).isEqualTo(1);
        verify(storageService).deleteFromStorage("storage-key-123");
        assertThat(eligibleObject.getStatus()).isEqualTo(StorageObjectStatus.PURGED);
        assertThat(eligibleObject.getLastError()).isNull();
        verify(physicalStorageObjectRepository).save(eligibleObject);
        verify(storageService).cleanOrphanedStagingFiles(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Abort deletion and reactivate object if active or bin files acquired reference concurrently")
    void runGarbageCollection_AbortsIfNewReferenceDetected() {
        when(physicalStorageObjectRepository.findByStatusAndReferenceCount(StorageObjectStatus.GC_ELIGIBLE, 0))
                .thenReturn(List.of(eligibleObject));
        // Concurrent file upload or restore created a new reference
        when(fileRepository.countFilesWithStatuses(eq("sha256-unreferenced"), anyList()))
                .thenReturn(1L);

        int purgedCount = garbageCollectionService.runGarbageCollection();

        assertThat(purgedCount).isEqualTo(0);
        verify(storageService, never()).deleteFromStorage(anyString());
        assertThat(eligibleObject.getStatus()).isEqualTo(StorageObjectStatus.ACTIVE);
        assertThat(eligibleObject.getReferenceCount()).isEqualTo(1);
        assertThat(eligibleObject.getGcEligibleAt()).isNull();
        verify(physicalStorageObjectRepository).save(eligibleObject);
    }

    @Test
    @DisplayName("Handle physical deletion failure by recording error and incrementing failureCount for safe retry")
    void runGarbageCollection_RecordsFailureOnStorageException() {
        when(physicalStorageObjectRepository.findByStatusAndReferenceCount(StorageObjectStatus.GC_ELIGIBLE, 0))
                .thenReturn(List.of(eligibleObject));
        when(fileRepository.countFilesWithStatuses(eq("sha256-unreferenced"), anyList()))
                .thenReturn(0L);
        doThrow(new StorageException("Disk I/O error")).when(storageService).deleteFromStorage("storage-key-123");

        int purgedCount = garbageCollectionService.runGarbageCollection();

        assertThat(purgedCount).isEqualTo(0);
        assertThat(eligibleObject.getFailureCount()).isEqualTo(1);
        assertThat(eligibleObject.getLastError()).contains("Disk I/O error");
        assertThat(eligibleObject.getStatus()).isEqualTo(StorageObjectStatus.GC_ELIGIBLE);
        verify(physicalStorageObjectRepository).save(eligibleObject);
    }

    @Test
    @DisplayName("No-op when no storage objects are eligible for garbage collection")
    void runGarbageCollection_EmptyEligibleList() {
        when(physicalStorageObjectRepository.findByStatusAndReferenceCount(StorageObjectStatus.GC_ELIGIBLE, 0))
                .thenReturn(Collections.emptyList());

        int purgedCount = garbageCollectionService.runGarbageCollection();

        assertThat(purgedCount).isEqualTo(0);
        verify(storageService, never()).deleteFromStorage(anyString());
        verify(physicalStorageObjectRepository, never()).save(any());
    }
}
