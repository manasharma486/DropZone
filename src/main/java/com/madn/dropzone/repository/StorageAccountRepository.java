package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StorageAccountRepository extends JpaRepository<StorageAccount, UUID> {
    Optional<StorageAccount> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StorageAccount s WHERE s.id = :id")
    Optional<StorageAccount> findByIdWithLock(@Param("id") UUID id);
}
