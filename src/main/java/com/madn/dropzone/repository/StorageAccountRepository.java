package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StorageAccountRepository extends JpaRepository<StorageAccount,UUID>{
    Optional<StorageAccount> findByUserId(UUID userId);

}
