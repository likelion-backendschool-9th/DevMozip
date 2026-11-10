package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.PortfolioAnalysisResponse;
import com.devmozip.backend.dto.ai.PortfolioAnalysisResponse.Portfolio;
import com.devmozip.backend.repository.AiPortfolioAnalysisRepository;
import com.devmozip.backend.repository.AiPortfolioAnalysisRepository.Result;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 짧은 일관 읽기 → DB CAS 임대 → 트랜잭션 밖 모델 → 현재 권한/자료 재확인 → CAS 저장. */
@Service
public class AiPortfolioAnalysisService {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiPortfolioAnalysisService.class);
	public record Stored(int version, String status, String token, LocalDateTime leaseUntil, String fingerprint,
		PortfolioAnalysisResponse response, Integer errorStatus) {}
	private record Input(Result result, String fingerprint, List<AiPortfolioSelection.Criterion> criteria,
		List<Portfolio> portfolios, List<AiPortfolioAnalysisModel.Document> documents) {}
	private final AiPortfolioAnalysisRepository repository;
	private final CurrentUserProvider currentUser;
	private final AiPortfolioSelection selection;
	private final AiPortfolioAnalysisModel model;
	private final ObjectMapper mapper;
	private final TransactionTemplate read;
	public AiPortfolioAnalysisService(AiPortfolioAnalysisRepository repository, CurrentUserProvider currentUser,
		AiPortfolioSelection selection, AiPortfolioAnalysisModel model, ObjectMapper mapper, PlatformTransactionManager manager) {
		this.repository = repository; this.currentUser = currentUser; this.selection = selection; this.model = model;
		this.mapper = mapper;
		read = new TransactionTemplate(manager);
		read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		read.setReadOnly(true);
		read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}
	public PortfolioAnalysisResponse get(long id) { return perform(id, false); }
	public PortfolioAnalysisResponse generate(long id) { return perform(id, true); }
	private PortfolioAnalysisResponse perform(long id, boolean generate) {
		if (id <= 0) throw error(HttpStatus.BAD_REQUEST, "추천 결과 ID를 확인해 주세요.");
		long viewer = currentUser.getCurrentUserId();
		Input input = load(id, viewer);
		Stored previous = decode(input.result().stored());
		if (previous != null && previous.status().equals("PENDING") && previous.leaseUntil() != null
			&& previous.leaseUntil().isAfter(now())) return empty(id, "PENDING", "상세 분석을 진행 중입니다. 잠시 후 다시 조회해 주세요.");
		if (previous != null && Set.of("READY", "NO_DATA").contains(previous.status())
			&& input.fingerprint().equals(previous.fingerprint()) && previous.response() != null) {
			var response = previous.response();
			return new PortfolioAnalysisResponse(id, response.status(), true, response.generatedAt(), response.portfolios(),
				response.assessments(), response.questions(), response.notice());
		}
		if (!generate) return empty(id, previous == null ? "NOT_GENERATED" : previous.status().equals("FAILED") ? "FAILED" : "STALE",
			previous == null ? "상세 분석을 요청해 주세요." : "자료가 변경됐거나 이전 분석이 완료되지 않았습니다. 다시 분석해 주세요.");
		String token = UUID.randomUUID().toString();
		String pending = encode(new Stored(1, "PENDING", token, now().plusSeconds(180), input.fingerprint(), null, null));
		if (!replace(id, input.result().stored(), pending)) return empty(id, "PENDING", "다른 요청이 처리 중입니다. 다시 조회해 주세요.");
		long deadline = System.nanoTime() + java.time.Duration.ofSeconds(180).toNanos();
		try {
			Input before = load(id, viewer);
			if (!before.fingerprint().equals(input.fingerprint())) throw error(HttpStatus.CONFLICT, "자료가 변경되었습니다. 새 자료로 다시 분석해 주세요.");
			var assessments = input.documents().isEmpty() ? List.<PortfolioAnalysisResponse.Assessment>of()
				: model.analyze(input.criteria(), input.documents(), deadline);
			var questions = assessments.stream().filter(a -> a.status().equals("UNKNOWN") || a.status().equals("PARTIAL"))
				.map(a -> "협업 전 확인: ‘" + a.requirement() + "’에 대한 구체적인 담당 범위와 구현 사례를 확인해 주세요.").limit(8).toList();
			var response = new PortfolioAnalysisResponse(id, input.documents().isEmpty() ? "NO_DATA" : "READY", false, now(),
				input.portfolios(), assessments, questions, input.documents().isEmpty()
					? "요구사항과 관련 있고 본문이 있는 공개 포트폴리오가 없습니다. 기존 점수·순위는 유지됩니다."
					: "요청 당시 요구사항과 현재 열람 가능한 자료를 비교했습니다. 포트폴리오는 작성자의 자기서술이며 검증된 실력을 뜻하지 않습니다. 기존 점수·순위는 유지됩니다.");
			Input latest = load(id, viewer);
			if (System.nanoTime() >= deadline) throw error(HttpStatus.GATEWAY_TIMEOUT, "상세 분석 전체 제한 시간이 초과되었습니다.");
			if (!latest.fingerprint().equals(input.fingerprint())) throw error(HttpStatus.CONFLICT, "분석 중 자료가 변경되었습니다. 다시 분석해 주세요.");
			String complete = encode(new Stored(1, response.status(), token, null, input.fingerprint(), response, null));
			if (!replace(id, pending, complete)) throw error(HttpStatus.CONFLICT, "분석 요청 상태가 변경되었습니다. 다시 조회해 주세요.");
			// 저장 후에도 현재 권한과 자료를 확인한다. 회수된 본문 인용은 반환하지 않는다.
			if (!load(id, viewer).fingerprint().equals(input.fingerprint())) {
				replace(id, complete, null);
				throw error(HttpStatus.CONFLICT, "저장 후 자료가 변경되었습니다. 다시 분석해 주세요.");
			}
			return response;
		} catch (RuntimeException failure) {
			int status = failure instanceof ResponseStatusException known ? known.getStatusCode().value() : 502;
			try { replace(id, pending, encode(new Stored(1, "FAILED", token, null, input.fingerprint(), null, status))); }
			catch (RuntimeException cleanupFailure) {
				log.warn("상세 분석 {} 실패 상태 저장 불가; 임대 만료 후 재시도 가능 ({})", id, cleanupFailure.getClass().getSimpleName());
			}
			if (failure instanceof ResponseStatusException expected) throw expected;
			throw error(HttpStatus.BAD_GATEWAY, "상세 분석을 완료하지 못했습니다. 다시 시도해 주세요.");
		}
	}
	private Input load(long id, long viewer) {
		try { return read.execute(tx -> {
			Result result = repository.result(id, viewer).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "추천 결과를 찾을 수 없습니다."));
			entitled(viewer);
			if (result.developerId() == null) throw error(HttpStatus.BAD_REQUEST, "개발자 추천 결과만 상세 분석할 수 있습니다.");
			if (!"SUCCESS".equals(result.requestStatus())) throw error(HttpStatus.CONFLICT, "완료된 추천 결과가 아닙니다.");
			var target = repository.account(result.developerId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "공개 개발자를 찾을 수 없습니다."));
			if (!"ACTIVE".equals(target.status()) || target.tag() == null || target.tag().isBlank())
				throw error(HttpStatus.NOT_FOUND, "현재 공개 개발자 정보를 열람할 수 없습니다.");
			var metadata = new ArrayList<AiPortfolioAnalysisRepository.Metadata>();
			long cursor = 0;
			while (true) {
				var batch = repository.metadata(result.developerId(), cursor);
				if (batch.isEmpty()) break;
				metadata.addAll(batch); cursor = batch.getLast().id();
			}
			var criteria = selection.criteria(result.conditions());
			var selected = selection.choose(metadata, criteria);
			var documents = new ArrayList<AiPortfolioAnalysisModel.Document>();
			var portfolios = new ArrayList<Portfolio>();
			var bodyVersions = new ArrayList<String>();
			for (var selectedItem : selected) {
				var row = selectedItem.metadata();
				var body = repository.body(row.id(), result.developerId()).orElseThrow(() -> error(HttpStatus.CONFLICT, "포트폴리오 공개 범위가 변경되었습니다."));
				String text = selection.clean(body.description(), 6000);
				bodyVersions.add(row.id() + ":" + hash(body.description() == null ? "" : body.description()));
				if (text.isBlank()) continue;
				documents.add(new AiPortfolioAnalysisModel.Document(row.id(), selection.clean(row.title(), 1000),
					selection.clean(row.summary(), 3000), selection.clean(row.role(), 500), selection.clean(row.skills(), 3000), text));
				portfolios.add(new Portfolio(row.id(), selection.clean(row.title(), 1000),
					"공개 제목·요약·역할·기술에 다음 요청 조건의 관련 표현이 있습니다: " + String.join(" · ", selectedItem.matched()),
					selection.clean(body.description(), Integer.MAX_VALUE).length() > 6000));
			}
			entitled(viewer);
			String fingerprint = hash(mapper.writeValueAsString(List.of(model.version(), result.conditions(), metadata, bodyVersions)));
			return new Input(result, fingerprint, criteria, List.copyOf(portfolios), List.copyOf(documents));
		}); } catch (DataAccessException unavailable) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "상세 분석 자료 저장소를 확인할 수 없습니다."); }
		catch (tools.jackson.core.JacksonException malformed) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "저장된 추천 조건 형식을 확인할 수 없습니다."); }
	}
	private void entitled(long viewer) {
		var account = repository.account(viewer).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "로그인 사용자를 찾을 수 없습니다."));
		if (!Set.of("ACTIVE", "WARNED").contains(account.status() == null ? "" : account.status()))
			throw error(HttpStatus.FORBIDDEN, "이 계정으로 상세 분석을 열람할 수 없습니다.");
		if (!Set.of("PREMIUM", "ADMIN").contains(account.role() == null ? "" : account.role())) throw error(HttpStatus.FORBIDDEN, "프리미엄 회원만 상세 분석을 이용할 수 있습니다.");
		var subscription = repository.latestSubscription(viewer);
		if (subscription.isEmpty()) {
			throw error(HttpStatus.FORBIDDEN, "유효한 프리미엄 구독이 필요합니다.");
		}
		var value = subscription.get();
		var time = now();
		if (!Set.of("ACTIVE", "RENEWING", "CANCEL_REQUESTED").contains(value.status() == null ? "" : value.status())
			|| value.startsAt() == null || value.periodEnd() == null || time.isBefore(value.startsAt())
			|| !time.isBefore(value.periodEnd()) || value.endedAt() != null && !time.isBefore(value.endedAt()))
			throw error(HttpStatus.FORBIDDEN, "프리미엄 구독이 만료되었거나 이용 기간이 아닙니다.");
	}
	private Stored decode(String raw) {
		if (raw == null || !raw.startsWith("{")) return null;
		try {
			var stored = mapper.readValue(raw, Stored.class);
			return stored.version() == 1 && stored.status() != null ? stored : null;
		} catch (RuntimeException malformed) { return null; }
	}
	private String encode(Stored stored) { return mapper.writeValueAsString(stored); }
	private boolean replace(long id, String expected, String value) {
		try { return repository.replace(id, expected, value); }
		catch (DataAccessException unavailable) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "상세 분석 상태를 저장할 수 없습니다."); }
	}
	private PortfolioAnalysisResponse empty(long id, String status, String notice) {
		return new PortfolioAnalysisResponse(id, status, false, null, List.of(), List.of(), List.of(), notice);
	}
	private String hash(String text) {
		try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
		catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible); }
	}
	private LocalDateTime now() {
		try { return repository.currentTime(); }
		catch (DataAccessException unavailable) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "구독 및 분석 기준 시각을 확인할 수 없습니다."); }
	}
	private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
