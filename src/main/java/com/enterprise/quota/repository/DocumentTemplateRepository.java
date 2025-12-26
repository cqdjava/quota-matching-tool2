package com.enterprise.quota.repository;

import com.enterprise.quota.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    List<DocumentTemplate> findAllByOrderByUpdatedAtDesc();
    List<DocumentTemplate> findByTemplateNameContainingIgnoreCase(String keyword);
}

