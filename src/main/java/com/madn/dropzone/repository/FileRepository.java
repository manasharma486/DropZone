package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<StoredFile> getStoredFileFromStatus(
            @Param("userId") UUID userId,
            @Param("status") FileStatus status
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM StoredFile f WHERE f.status = :status AND f.storageAccount.id = :userId")
    void removeAllByStatus(@Param("status") FileStatus status , @Param("userId") UUID id);


    @Query("""
    SELECT f FROM StoredFile f
    WHERE  f.storageAccount.id = :userId
      AND f.checksum = :checksum
    """)
    Optional<StoredFile> existFileInAccount(
            @Param("checksum") String checksum,
            @Param("userId") UUID id
    );

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.checksum = :checksum
    """)
    List<StoredFile> existFileInStorage(
            @Param("checksum") String checksum
    );

}
