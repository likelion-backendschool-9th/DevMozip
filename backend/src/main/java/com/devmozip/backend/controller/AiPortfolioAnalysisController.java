package com.devmozip.backend.controller;

import com.devmozip.backend.dto.ai.PortfolioAnalysisResponse;
import com.devmozip.backend.service.AiPortfolioAnalysisService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/recommendations/{id}/detailed-analysis")
public class AiPortfolioAnalysisController {
	private final AiPortfolioAnalysisService service;
	public AiPortfolioAnalysisController(AiPortfolioAnalysisService service) { this.service = service; }
	@GetMapping
	public ResponseEntity<PortfolioAnalysisResponse> get(@PathVariable long id) { return response(service.get(id)); }
	@PostMapping
	public ResponseEntity<PortfolioAnalysisResponse> generate(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
		if (body != null && !body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상세 분석은 추천 결과 ID만 사용합니다.");
		return response(service.generate(id));
	}
	private ResponseEntity<PortfolioAnalysisResponse> response(PortfolioAnalysisResponse value) {
		var builder = ResponseEntity.status(value.status().equals("PENDING") ? 202 : 200).header("Cache-Control", "no-store");
		if (value.status().equals("PENDING")) builder.header("Retry-After", "3");
		return builder.body(value);
	}
}
