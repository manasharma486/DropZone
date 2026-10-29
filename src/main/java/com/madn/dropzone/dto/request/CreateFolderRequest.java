package com.madn.dropzone.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

    @JsonAlias({"folderName"})
    @NotBlank(message = "Folder name cannot be blank")
    @Size(min = 1, max = 255, message = "Folder name must be between 1 and 255 characters")
    @Pattern(
        regexp = "^[^\\\\/:*?\"<>|]+$", 
        message = "Folder name cannot contain characters: \\ / : * ? \" < > |"
    )
    private String name;

    @JsonAlias({"parentFolder", "destinationFolderId"})
    private UUID parentFolderId; // Null indicates Drive root

    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter("parentFolderId")
    public void setParentFolderId(UUID value) {
        this.parentFolderId = value;
    }
}
