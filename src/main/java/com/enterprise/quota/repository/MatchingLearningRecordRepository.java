package com.enterprise.quota.repository;

import com.enterprise.quota.entity.MatchingLearningRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchingLearningRecordRepository extends JpaRepository<MatchingLearningRecord, Long> {
    
    List<MatchingLearningRecord> findByMatchTypeOrderByCreateTimeDesc(Integer matchType);
    
    List<MatchingLearningRecord> findAllByOrderByCreateTimeDesc();
}

