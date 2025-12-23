package com.enterprise.quota.repository;

import com.enterprise.quota.entity.QuotaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuotaVersionRepository extends JpaRepository<QuotaVersion, Long> {
    
    List<QuotaVersion> findAllByOrderByCreateTimeDesc();
    
    QuotaVersion findByVersionName(String versionName);
}

