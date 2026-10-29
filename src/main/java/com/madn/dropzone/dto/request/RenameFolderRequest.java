package com.madn.dropzone.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenameFolderRequest {

    @JsonAlias({"folderName", "newName"})
    @NotBlank(message = "New folder name cannot be blank")
    @Size(min = 1, max = 255, message = "Folder name must be between 1 and 255 characters")
    @Pattern(
        regexp = "^[^\\\\/:*?\"<>|]+$", 
        message = "Folder name cannot contain characters: \\ / : * ? \" < > |"
    )
    private String name;
}
