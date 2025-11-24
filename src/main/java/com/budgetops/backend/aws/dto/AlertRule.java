package com.budgetops.backend.aws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 알림 규칙 정보
 * yaml 파일의 rule 하나를 파싱한 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {
    /**
     * 규칙 ID
     */
    private String id;
    
    /**
     * 규칙 제목
     */
    private String title;
    
    /**
     * 규칙 설명
     */
    private String description;
    
    /**
     * 알림 조건 목록
     */
    private List<AlertCondition> conditions;
    
    /**
     * 권장사항
     */
    private String recommendation;
    
    /**
     * 예상 절감액
     */
    private String costSaving;
}

