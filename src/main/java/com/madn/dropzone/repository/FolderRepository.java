package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<StoredFolder , UUID> {
    @Query("""
    SELECT f from StoredFolder  f
    where f.id = :folderid
    and f.storageAccount.id = :userid
""")
    Optional<StoredFolder> getStoredFolderByFolderidAndUserId(@Param("folderid") UUID folderid,
                                                              @Param("userid") UUID userid);
}
