package com.enterprise.quota.repository;

import com.enterprise.quota.entity.ReplacementTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReplacementTemplateRepository extends JpaRepository<ReplacementTemplate, Long> {
    List<ReplacementTemplate> findAllByOrderByUpdatedAtDesc();
    List<ReplacementTemplate> findByTemplateNameContainingIgnoreCase(String keyword);
}

