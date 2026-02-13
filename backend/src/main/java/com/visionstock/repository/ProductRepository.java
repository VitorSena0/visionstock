package com.visionstock.repository;

import com.visionstock.model.inventory.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);
}
