package com.enterprise.quota.repository;

import com.enterprise.quota.entity.EnterpriseQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnterpriseQuotaRepository extends JpaRepository<EnterpriseQuota, Long> {
    
    List<EnterpriseQuota> findByQuotaNameContaining(String quotaName);
    List<EnterpriseQuota> findByFeatureValueContaining(String featureValue);
    EnterpriseQuota findByQuotaCode(String quotaCode);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.quotaName LIKE %:keyword% OR e.featureValue LIKE %:keyword%")
    List<EnterpriseQuota> findByKeyword(@Param("keyword") String keyword);
}

