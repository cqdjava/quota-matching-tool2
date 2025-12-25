package com.enterprise.quota.repository;

import com.enterprise.quota.entity.KeywordWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KeywordWeightRepository extends JpaRepository<KeywordWeight, Long> {
    
    Optional<KeywordWeight> findByKeyword(String keyword);
}

