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
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.quotaName LIKE CONCAT('%', :keyword, '%') OR e.featureValue LIKE CONCAT('%', :keyword, '%')")
    List<EnterpriseQuota> findByKeyword(@Param("keyword") String keyword);
    
    // 版本相关查询
    List<EnterpriseQuota> findByVersionId(Long versionId);

    /**
     * 指定版本下的定额，以及未绑定版本（version_id 为空）的历史数据。
     * 旧版导入可能未写入 version_id，导出仍可见全部数据；明细列表应对齐可见性。
     */
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId OR e.versionId IS NULL ORDER BY e.id ASC")
    List<EnterpriseQuota> findByVersionIdIncludingUnassigned(@Param("versionId") Long versionId);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND (e.quotaName LIKE CONCAT('%', :keyword, '%') OR e.featureValue LIKE CONCAT('%', :keyword, '%'))")
    List<EnterpriseQuota> findByVersionIdAndKeyword(@Param("versionId") Long versionId, @Param("keyword") String keyword);

    @Query("SELECT e FROM EnterpriseQuota e WHERE (e.versionId = :versionId OR e.versionId IS NULL) AND (e.quotaName LIKE CONCAT('%', :keyword, '%') OR e.featureValue LIKE CONCAT('%', :keyword, '%')) ORDER BY e.id ASC")
    List<EnterpriseQuota> findByVersionIdAndKeywordIncludingUnassigned(@Param("versionId") Long versionId, @Param("keyword") String keyword);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND e.quotaName LIKE CONCAT('%', :quotaName, '%')")
    List<EnterpriseQuota> findByVersionIdAndQuotaNameContaining(@Param("versionId") Long versionId, @Param("quotaName") String quotaName);
    
    @Query("SELECT e FROM EnterpriseQuota e WHERE e.versionId = :versionId AND e.featureValue LIKE CONCAT('%', :featureValue, '%')")
    List<EnterpriseQuota> findByVersionIdAndFeatureValueContaining(@Param("versionId") Long versionId, @Param("featureValue") String featureValue);
    
    // 删除指定版本的所有定额
    void deleteByVersionId(Long versionId);
}

