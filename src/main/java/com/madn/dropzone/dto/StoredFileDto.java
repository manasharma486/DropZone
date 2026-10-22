package com.madn.dropzone.dto;

import com.madn.dropzone.enums.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StoredFileDto {

    private String fileName;
    private Long fileSize;
    private String contentType;
    private LocalDateTime uploaded_at;
    private FileStatus status;

}
