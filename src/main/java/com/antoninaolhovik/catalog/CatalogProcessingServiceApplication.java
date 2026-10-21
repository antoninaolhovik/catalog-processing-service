package com.antoninaolhovik.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CatalogProcessingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CatalogProcessingServiceApplication.class, args);
	}
}
