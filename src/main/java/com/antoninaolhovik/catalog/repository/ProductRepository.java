package com.antoninaolhovik.catalog.repository;

import com.antoninaolhovik.catalog.model.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuAndPartnerId(String sku, String partnerId);
}
