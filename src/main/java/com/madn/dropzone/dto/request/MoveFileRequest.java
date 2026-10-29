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
public class MoveFileRequest {
    private UUID destinationFolderId; // Null indicates Drive root

    @JsonSetter("destinationFolderId")
    @JsonAlias({"folderId", "folderid", "destinationFolder", "parentFolderId"})
    public void setDestinationFolderId(UUID value) {
        this.destinationFolderId = value;
    }
}
