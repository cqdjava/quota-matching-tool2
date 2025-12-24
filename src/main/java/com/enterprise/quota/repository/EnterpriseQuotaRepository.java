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
    
    // 版本相关查询
    List<EnterpriseQuota> findByVersionId(Long versionId);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND (e.quotaName LIKE %:keyword% OR e.featureValue LIKE %:keyword%)")
    List<EnterpriseQuota> findByVersionIdAndKeyword(@Param("versionId") Long versionId, @Param("keyword") String keyword);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND e.quotaName LIKE %:quotaName%")
    List<EnterpriseQuota> findByVersionIdAndQuotaNameContaining(@Param("versionId") Long versionId, @Param("quotaName") String quotaName);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND e.featureValue LIKE %:featureValue%")
    List<EnterpriseQuota> findByVersionIdAndFeatureValueContaining(@Param("versionId") Long versionId, @Param("featureValue") String featureValue);
    
    // 删除指定版本的所有定额
    void deleteByVersionId(Long versionId);
}

