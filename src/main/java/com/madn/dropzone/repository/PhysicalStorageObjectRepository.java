package com.madn.dropzone.repository;

import com.madn.dropzone.entity.PhysicalStorageObject;
import com.madn.dropzone.enums.StorageObjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhysicalStorageObjectRepository extends JpaRepository<PhysicalStorageObject, String> {

    Optional<PhysicalStorageObject> findByChecksum(String checksum);

    Optional<PhysicalStorageObject> findByStorageKey(String storageKey);

    List<PhysicalStorageObject> findByStatus(StorageObjectStatus status);

    List<PhysicalStorageObject> findByStatusAndReferenceCount(StorageObjectStatus status, int referenceCount);

    @Modifying
    @Query("UPDATE PhysicalStorageObject p SET " +
           "p.referenceCount = p.referenceCount + 1, " +
           "p.status = com.madn.dropzone.enums.StorageObjectStatus.ACTIVE, " +
           "p.gcEligibleAt = null, " +
           "p.lastVerifiedAt = CURRENT_TIMESTAMP " +
           "WHERE p.checksum = :checksum")
    int incrementReferenceCount(@Param("checksum") String checksum);

    @Modifying
    @Query("UPDATE PhysicalStorageObject p SET " +
           "p.referenceCount = CASE WHEN p.referenceCount > 0 THEN p.referenceCount - 1 ELSE 0 END, " +
           "p.status = CASE WHEN p.referenceCount <= 1 THEN com.madn.dropzone.enums.StorageObjectStatus.GC_ELIGIBLE ELSE p.status END, " +
           "p.gcEligibleAt = CASE WHEN p.referenceCount <= 1 THEN CURRENT_TIMESTAMP ELSE p.gcEligibleAt END, " +
           "p.lastVerifiedAt = CURRENT_TIMESTAMP " +
           "WHERE p.checksum = :checksum")
    int decrementReferenceCount(@Param("checksum") String checksum);

    @Modifying
    @Query("UPDATE PhysicalStorageObject p SET p.status = :status, p.lastVerifiedAt = CURRENT_TIMESTAMP WHERE p.checksum = :checksum")
    int updateStatus(@Param("checksum") String checksum, @Param("status") StorageObjectStatus status);

    List<PhysicalStorageObject> findByReferenceCountLessThanEqual(int referenceCount);
}
