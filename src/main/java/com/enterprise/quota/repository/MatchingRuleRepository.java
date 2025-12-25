package com.enterprise.quota.repository;

import com.enterprise.quota.entity.MatchingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchingRuleRepository extends JpaRepository<MatchingRule, Long> {
    
    List<MatchingRule> findByRuleTypeOrderByConfidenceDesc(String ruleType);
    
    List<MatchingRule> findByRuleTypeAndConfidenceGreaterThanOrderByConfidenceDesc(String ruleType, Double minConfidence);
}

