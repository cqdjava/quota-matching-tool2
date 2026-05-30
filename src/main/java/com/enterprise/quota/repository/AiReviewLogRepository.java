package com.enterprise.quota.repository;

import com.enterprise.quota.entity.AiReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 复核日志仓库
 */
@Repository
public interface AiReviewLogRepository extends JpaRepository<AiReviewLog, Long> {

    /** 查找已确认的案例（人工修正或已采纳的 AI 建议），用于 Few-Shot 检索 */
    List<AiReviewLog> findByIsAcceptedTrueOrderByCreateTimeDesc();

    /** 按用户查找 */
    List<AiReviewLog> findByUserIdOrderByCreateTimeDesc(Long userId);

    /** 按清单项查找 */
    List<AiReviewLog> findByItemIdOrderByCreateTimeDesc(Long itemId);
}
