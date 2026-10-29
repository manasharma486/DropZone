package com.madn.dropzone.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbItem {
    private UUID id;       // Null for root
    private String name;   // "My Drive" for root
}
