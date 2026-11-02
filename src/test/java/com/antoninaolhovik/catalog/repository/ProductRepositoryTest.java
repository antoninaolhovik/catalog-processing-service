package com.antoninaolhovik.catalog.repository;

import com.antoninaolhovik.catalog.TestcontainersConfiguration;
import com.antoninaolhovik.catalog.model.Category;
import com.antoninaolhovik.catalog.model.Product;
import com.antoninaolhovik.catalog.model.ProductState;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSaveAndReadProductWithCategories() {
        Product product = newProduct("802999", "PARTNER-A");
        product.addCategory(new Category("Fruits"));
        product.addCategory(new Category("Bundles"));

        Long id = productRepository.saveAndFlush(product).getId();
        entityManager.clear();

        Product found = productRepository.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("Apple Fruit");
        assertThat(found.getPrice()).isEqualByComparingTo("41238.00");
        assertThat(found.getSpecialFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(found.getState()).isEqualTo(ProductState.ACTIVE);
        assertThat(found.getImportedAt()).isEqualTo(Instant.parse("2026-03-23T10:30:00Z"));
        assertThat(found.getCategories()).extracting(Category::getName)
                .containsExactlyInAnyOrder("Fruits", "Bundles");
    }

    @Test
    void shouldFindBySkuAndPartnerId() {
        productRepository.saveAndFlush(newProduct("802999", "PARTNER-A"));
        entityManager.clear();

        assertThat(productRepository.findBySkuAndPartnerId("802999", "PARTNER-A")).isPresent();
        assertThat(productRepository.findBySkuAndPartnerId("802999", "PARTNER-B")).isEmpty();
    }

    @Test
    void shouldReplaceCategories() {
        Product product = newProduct("802999", "PARTNER-A");
        product.addCategory(new Category("Fruits"));
        productRepository.saveAndFlush(product);

        product.replaceCategories(Set.of(new Category("Bundles")));
        productRepository.saveAndFlush(product);
        entityManager.clear();

        Product found = productRepository.findBySkuAndPartnerId("802999", "PARTNER-A").orElseThrow();
        assertThat(found.getCategories()).extracting(Category::getName).containsExactly("Bundles");
    }

    private Product newProduct(String sku, String partnerId) {
        Product product = new Product();
        product.setName("Apple Fruit");
        product.setSku(sku);
        product.setPrice(new BigDecimal("41238.00"));
        product.setSpecialPrice(new BigDecimal("35000.00"));
        product.setSpecialFrom(LocalDate.of(2026, 3, 1));
        product.setSpecialTo(LocalDate.of(2026, 4, 1));
        product.setState(ProductState.ACTIVE);
        product.setBrand("Shelf 3");
        product.setImageUrl("https://img.example.com/apple.jpg");
        product.setPartnerId(partnerId);
        product.setSourceFileName("products_" + partnerId + "_2026-03-23.json");
        product.setImportedAt(Instant.parse("2026-03-23T10:30:00Z"));
        return product;
    }
}
