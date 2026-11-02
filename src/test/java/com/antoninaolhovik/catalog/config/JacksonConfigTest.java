package com.antoninaolhovik.catalog.config;

import com.antoninaolhovik.catalog.TestcontainersConfiguration;
import com.antoninaolhovik.catalog.dto.ProductDto;
import com.antoninaolhovik.catalog.dto.ProductExportFileDto;
import com.antoninaolhovik.catalog.model.ProductState;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.csv.CsvMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JacksonConfigTest {

    private static final String VALID_ALL_FIELDS = "/test-data/parser/json/valid_all_fields.json";

    @Autowired
    private ObjectMapper primaryMapper;

    @Autowired
    @Qualifier("csvObjectMapper")
    private ObjectMapper csvObjectMapper;

    @Test
    void shouldHaveAutoConfiguredJsonMapper() {
        assertThat(primaryMapper).isInstanceOf(JsonMapper.class);
    }

    @Test
    void csvObjectMapperShouldBeCsvMapperAndDistinctFromJsonMapper() {
        assertThat(csvObjectMapper).isInstanceOf(CsvMapper.class).isNotSameAs(primaryMapper);
    }

    @Test
    void shouldDeserializeJsonIntoProductExportFileDto() throws IOException {
        ProductExportFileDto dto;
        try (InputStream json = getClass().getResourceAsStream(VALID_ALL_FIELDS)) {
            dto = primaryMapper.readValue(json, ProductExportFileDto.class);
        }

        assertThat(dto.partnerId()).isEqualTo("PARTNER-A");
        assertThat(dto.exportDate()).isEqualTo(Instant.parse("2026-03-23T10:30:00Z"));
        assertThat(dto.products()).hasSize(1);

        ProductDto product = dto.products().getFirst();
        assertThat(product.name()).isEqualTo("Apple Fruit");
        assertThat(product.sku()).isEqualTo("802999");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("41238.0"));
        assertThat(product.specialPrice()).isEqualByComparingTo(new BigDecimal("35000.0"));
        assertThat(product.specialFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(product.specialTo()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(product.state()).isEqualTo(ProductState.ACTIVE);
        assertThat(product.brand()).isEqualTo("Shelf 3");
        assertThat(product.categories()).isEqualTo(List.of("Golden apple Bundles"));
        assertThat(product.imageUrl()).isEqualTo("https://img.example.com/apple.jpg");
    }
}
