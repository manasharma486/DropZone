package com.madn.dropzone.repository;

import com.madn.dropzone.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileRepository extends JpaRepository<StoredFile , UUID> {
}
