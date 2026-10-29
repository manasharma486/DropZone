package com.madn.dropzone.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinActionRequest {
    @JsonAlias({"fileIDs", "fileIdList", "ids"})
    @NotEmpty(message = "At least one file ID must be specified")
    private List<UUID> fileIds;
}
