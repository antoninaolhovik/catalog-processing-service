package com.antoninaolhovik.catalog.dto;

import java.time.Instant;
import java.util.List;

public record ProductExportFileDto(String partnerId,
                                   Instant exportDate,
                                   List<ProductDto> products) {
}
