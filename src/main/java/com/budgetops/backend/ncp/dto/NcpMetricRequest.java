package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * NCP Cloud Insight DataQueryRequest
 * Cloud Insight API로 메트릭 데이터를 조회하기 위한 요청 DTO
 */
@Getter
@Builder
public class NcpMetricRequest {

    /**
     * 조회 시작 시간 (밀리초, Unix Timestamp)
     */
    private Long timeStart;

    /**
     * 조회 종료 시간 (밀리초, Unix Timestamp)
     */
    private Long timeEnd;

    /**
     * 상품명 (예: "System/Server(VPC)")
     */
    private String productName;

    /**
     * 상품의 cw_key (Server VPC: "460438474722512896")
     */
    private String cw_key;

    /**
     * 조회하려는 Metric 이름
     * (예: avg_cpu_used_rto, mem_usert, avg_snd_bps 등)
     */
    private String metric;

    /**
     * 데이터 집계 주기
     * Min1(기본값) | Min5 | Min30 | Hour2 | Day1
     */
    @Builder.Default
    private String interval = "Min1";

    /**
     * 집계 함수
     * COUNT | SUM | MAX | MIN | AVG(기본값)
     */
    @Builder.Default
    private String aggregation = "AVG";

    /**
     * 쿼리 기준이 충분하지 않을 때 쿼리 결과를 처리하는 방법
     * COUNT | SUM | MAX | MIN | AVG(기본값)
     */
    @Builder.Default
    private String queryAggregation = "AVG";

    /**
     * 조회하려는 Dimension
     * (예: {"instanceNo": "12345", "type": "svr"})
     */
    private Map<String, String> dimensions;
}
