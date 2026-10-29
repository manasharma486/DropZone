package com.madn.dropzone.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveFolderRequest {
    private UUID destinationFolderId; // Null moves to Drive root

    @JsonSetter("destinationFolderId")
    @JsonAlias({"parentFolderId", "destinationFolder", "parentFolder", "folderId"})
    public void setDestinationFolderId(UUID value) {
        this.destinationFolderId = value;
    }
}
