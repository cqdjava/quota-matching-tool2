package com.enterprise.quota.repository;

import com.enterprise.quota.entity.ProjectItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectItemRepository extends JpaRepository<ProjectItem, Long> {
    
    List<ProjectItem> findByMatchStatus(Integer matchStatus);
    ProjectItem findByItemCode(String itemCode);
}

