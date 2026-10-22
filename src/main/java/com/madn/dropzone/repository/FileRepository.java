package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<StoredFile , UUID> {
    void removeStoredFileById(UUID id);
    Optional<StoredFile> getStoredFileById(UUID id);

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.storageAccount.id = :userId
    AND f.status = :status
    ORDER BY f.uploadedAt desc
""")
    List<StoredFile> getStoredFileInBin(
            @Param("userId") UUID userId,
            @Param("status") FileStatus status
    );
}
