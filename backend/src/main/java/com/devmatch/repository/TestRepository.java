package com.devmatch.repository;

import com.devmatch.entity.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    List<Test> findByCategoryAndIsActiveTrue(String category);

    List<Test> findByIsActiveTrue();
}
