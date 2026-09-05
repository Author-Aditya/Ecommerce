package com.product.manager.service;

import com.product.manager.dto.ProductRequest;
import com.product.manager.dto.ProductResponse;
import com.product.manager.entity.ProductEntity;
import com.product.manager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String productId = request.getProductId();
        if (productId == null || productId.isEmpty()) {
            productId = "prod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        ProductEntity product = ProductEntity.builder()
                .productId(productId)
                .productName(request.getProductName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .category(request.getCategory())
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        productRepository.save(product);

        return ProductResponse.builder()
                .productId(productId)
                .productName(request.getProductName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .category(request.getCategory())
                .message("Product created successfully")
                .build();
    }

    @Transactional
    public ProductResponse updateProduct(ProductRequest request) {
        ProductEntity existingProduct = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));

        existingProduct.setProductName(request.getProductName());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setQuantity(request.getQuantity());
        existingProduct.setCategory(request.getCategory());
        existingProduct.setUpdatedAt(java.time.LocalDateTime.now());

        productRepository.save(existingProduct);

        return ProductResponse.builder()
                .productId(request.getProductId())
                .productName(request.getProductName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .category(request.getCategory())
                .message("Product updated successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public ProductEntity getProductById(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
    }

    @Transactional(readOnly = true)
    public Iterable<ProductEntity> getAllProducts() {
        return productRepository.findAll();
    }
}
