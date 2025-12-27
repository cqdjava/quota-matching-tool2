package com.enterprise.quota.repository;

import com.enterprise.quota.entity.ProjectItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectItemRepository extends JpaRepository<ProjectItem, Long> {
    
    List<ProjectItem> findByMatchStatus(Integer matchStatus);
    ProjectItem findByItemCode(String itemCode);
    
    // 按用户ID查询清单项
    List<ProjectItem> findByUserId(Long userId);
    
    // 按用户ID和匹配状态查询
    List<ProjectItem> findByUserIdAndMatchStatus(Long userId, Integer matchStatus);
    
    // 删除指定用户的清单项
    void deleteByUserId(Long userId);
}

