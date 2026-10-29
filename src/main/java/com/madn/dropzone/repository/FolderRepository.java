package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<StoredFolder, UUID> {

    @Query("""
    SELECT f FROM StoredFolder f
    WHERE f.id = :folderid
      AND f.storageAccount.id = :userid
    """)
    Optional<StoredFolder> getStoredFolderByFolderidAndUserId(@Param("folderid") UUID folderid,
                                                              @Param("userid") UUID userid);

    Optional<StoredFolder> findByIdAndStorageAccountId(UUID id, UUID storageAccountId);

    @Query("""
    SELECT f FROM StoredFolder f
    WHERE f.storageAccount.id = :accountId
      AND f.parentFolder IS NULL
      AND (f.status = com.madn.dropzone.enums.FileStatus.ACTIVE OR f.status IS NULL)
    ORDER BY f.name ASC
    """)
    List<StoredFolder> findRootFolders(@Param("accountId") UUID accountId);

    @Query("""
    SELECT f FROM StoredFolder f
    WHERE f.storageAccount.id = :accountId
      AND f.parentFolder.id = :parentId
      AND (f.status = com.madn.dropzone.enums.FileStatus.ACTIVE OR f.status IS NULL)
    ORDER BY f.name ASC
    """)
    List<StoredFolder> findChildFolders(
            @Param("accountId") UUID accountId,
            @Param("parentId") UUID parentId
    );

    @Query("""
    SELECT f FROM StoredFolder f
    WHERE f.storageAccount.id = :accountId
      AND f.status = :status
    ORDER BY f.name ASC
    """)
    List<StoredFolder> findByStorageAccountIdAndStatus(
            @Param("accountId") UUID accountId,
            @Param("status") FileStatus status
    );

    @Query("""
    SELECT COUNT(f) > 0 FROM StoredFolder f
    WHERE f.storageAccount.id = :accountId
      AND ((:parentId IS NULL AND f.parentFolder IS NULL) OR f.parentFolder.id = :parentId)
      AND LOWER(f.name) = LOWER(:name)
      AND f.status = com.madn.dropzone.enums.FileStatus.ACTIVE
    """)
    boolean existsByStorageAccountIdAndParentFolderIdAndNameIgnoreCase(
            @Param("accountId") UUID accountId,
            @Param("parentId") UUID parentId,
            @Param("name") String name
    );

    @Query("""
    SELECT COUNT(f) > 0 FROM StoredFolder f
    WHERE f.storageAccount.id = :accountId
      AND ((:parentId IS NULL AND f.parentFolder IS NULL) OR f.parentFolder.id = :parentId)
      AND LOWER(f.name) = LOWER(:name)
      AND f.id != :excludeFolderId
      AND f.status = com.madn.dropzone.enums.FileStatus.ACTIVE
    """)
    boolean existsSiblingWithDifferentId(
            @Param("accountId") UUID accountId,
            @Param("parentId") UUID parentId,
            @Param("name") String name,
            @Param("excludeFolderId") UUID excludeFolderId
    );
}
