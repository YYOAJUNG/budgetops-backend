package com.budgetops.backend.simulator.controller;

import com.budgetops.backend.simulator.dto.*;
import com.budgetops.backend.simulator.service.ProposalService;
import com.budgetops.backend.simulator.service.RecommendationService;
import com.budgetops.backend.simulator.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UCAS (Universal Cost Action Simulator) API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {
    
    private final SimulationService simulationService;
    private final ProposalService proposalService;
    private final RecommendationService recommendationService;
    
    /**
     * 시뮬레이션 실행
     * POST /api/simulate
     */
    @PostMapping("/simulate")
    public ResponseEntity<SimulateResponse> simulate(@Valid @RequestBody SimulateRequest request) {
        log.info("Received simulation request: action={}, resourceCount={}", 
                request.getAction(), request.getResourceIds().size());
        
        SimulateResponse response = simulationService.simulate(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 제안서 생성
     * POST /api/proposals
     */
    @PostMapping("/proposals")
    public ResponseEntity<ProposalResponse> createProposal(@Valid @RequestBody ProposalRequest request) {
        log.info("Creating proposal: scenarioId={}", request.getScenarioId());
        
        ProposalResponse response = proposalService.createProposal(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 제안서 조회
     * GET /api/proposals/{proposalId}
     */
    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<ProposalResponse> getProposal(@PathVariable String proposalId) {
        ProposalResponse response = proposalService.getProposal(proposalId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 제안서 승인
     * POST /api/proposals/{proposalId}/approve
     */
    @PostMapping("/proposals/{proposalId}/approve")
    public ResponseEntity<ProposalResponse> approveProposal(@PathVariable String proposalId) {
        ProposalResponse response = proposalService.approveProposal(proposalId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 제안서 거부
     * POST /api/proposals/{proposalId}/reject
     */
    @PostMapping("/proposals/{proposalId}/reject")
    public ResponseEntity<ProposalResponse> rejectProposal(@PathVariable String proposalId) {
        ProposalResponse response = proposalService.rejectProposal(proposalId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Top 3 추천 액션 조회
     * GET /api/simulator/recommendations
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations() {
        List<RecommendationResponse> recommendations = recommendationService.getTopRecommendations();
        return ResponseEntity.ok(recommendations);
    }
}

