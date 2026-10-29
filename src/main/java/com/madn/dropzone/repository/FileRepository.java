package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<StoredFile , UUID> {
    void removeStoredFileById(UUID id);

    @Query("""
    SELECT f from StoredFile f
    WHERE f.storageAccount.id = :userid
    AND f.id= :fileid
""")
    Optional<StoredFile> getStoredFileByIdAndUserId(@Param("userid") UUID userid,
                                                    @Param("fileid") UUID fileid);

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.storageAccount.id = :userId
    AND f.status = :status
    AND (:type = '' OR f.contentType LIKE :type)
    ORDER BY f.uploadedAt DESC
""")
    List<StoredFile> getStoredFileFromStatus(
            @Param("userId") UUID userId,
            @Param("status") FileStatus status,
            @Param("type") String type
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

    @Query("""
    SELECT COUNT(f)
    FROM StoredFile f
    WHERE f.checksum = :checksum
    AND f.status IN :statuses
""")
    long countFilesWithStatuses(
            @Param("checksum") String checksum,
            @Param("statuses") Collection<FileStatus> statuses
    );

    void removeStoredFileByChecksum(String checksum);

    @Query("""
    SELECT f FROM StoredFile f
    WHERE LOWER(f.fileName) LIKE LOWER(CONCAT('%', :fileName, '%'))
    AND f.storageAccount.id = :userid
    AND f.status = :status
""")
    List<StoredFile> getStoredFilesByFileNameAndAccount(
            @Param("fileName") String fileName,
            @Param("userid") UUID userid,
            @Param("status") FileStatus status
    );

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.storageAccount.id = :userid
    AND f.status = :status
""")
    List<StoredFile> getStoredFilesByAccount(
            @Param("userid") UUID userid,
            @Param("status") FileStatus status,
            Sort sort
    );

    @Query("""
    SELECT f from StoredFile f
    where f.storageAccount.id = :userid
    AND f.folder.id = :folderid
    AND f.status = :status
""")
    List<StoredFile> getUserFilesInsideFolder(@Param("userid") UUID userid,
                                              @Param("folderid") UUID folderid,
                                              @Param("status") FileStatus status);

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.storageAccount.id = :accountId
      AND f.folder IS NULL
      AND f.status = :status
    """)
    org.springframework.data.domain.Page<StoredFile> findRootFiles(
            @Param("accountId") UUID accountId,
            @Param("status") FileStatus status,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
    SELECT f FROM StoredFile f
    WHERE f.storageAccount.id = :accountId
      AND f.folder.id = :folderId
      AND f.status = :status
    """)
    org.springframework.data.domain.Page<StoredFile> findFolderFiles(
            @Param("accountId") UUID accountId,
            @Param("folderId") UUID folderId,
            @Param("status") FileStatus status,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
    SELECT COUNT(f) FROM StoredFile f
    WHERE f.folder.id IN :folderIds AND f.status = :status
    """)
    long countByFolderIdInAndStatus(
            @Param("folderIds") Collection<UUID> folderIds,
            @Param("status") FileStatus status
    );

    @Query("""
    SELECT COALESCE(SUM(f.fileSize), 0) FROM StoredFile f
    WHERE f.folder.id IN :folderIds AND f.status = :status
    """)
    long sumSizeByFolderIdInAndStatus(
            @Param("folderIds") Collection<UUID> folderIds,
            @Param("status") FileStatus status
    );
}
