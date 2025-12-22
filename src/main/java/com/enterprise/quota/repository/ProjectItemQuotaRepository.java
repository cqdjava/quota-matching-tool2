package com.enterprise.quota.repository;

import com.enterprise.quota.entity.ProjectItemQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectItemQuotaRepository extends JpaRepository<ProjectItemQuota, Long> {
    
    /**
     * 根据清单项ID查找所有关联的定额
     */
    List<ProjectItemQuota> findByProjectItemIdOrderBySortOrderAsc(Long projectItemId);
    
    /**
     * 删除清单项的所有定额关联
     */
    @Modifying
    @Query("DELETE FROM ProjectItemQuota p WHERE p.projectItemId = ?1")
    void deleteByProjectItemId(Long projectItemId);
    
    /**
     * 统计清单项的定额数量
     */
    long countByProjectItemId(Long projectItemId);
}

