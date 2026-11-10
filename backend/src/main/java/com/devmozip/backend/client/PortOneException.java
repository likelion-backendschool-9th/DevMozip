package com.devmozip.backend.client;

/**
 * 포트원 API 호출 실패. 메시지에 빌링키·결제 정보를 넣지 않는다.
 * - REJECTED: 포트원이나 결제대행사가 요청을 거절했다(카드 거절 포함). 결제는 일어나지 않았다.
 * - NOT_FOUND: 대상(빌링키·결제)이 없다.
 * - UNKNOWN: 응답을 받지 못했거나 서버 오류다. 결제 요청이었다면 결과를 알 수 없다.
 */
public class PortOneException extends RuntimeException {
	public enum Kind { REJECTED, NOT_FOUND, UNKNOWN }

	private final Kind kind;
	private final int status;
	private final String type;

	public PortOneException(Kind kind, int status, String type) {
		super("포트원 요청 실패: kind=" + kind + ", status=" + status + ", type=" + type);
		this.kind = kind;
		this.status = status;
		this.type = type;
	}

	public Kind getKind() { return kind; }
	public int getStatus() { return status; }
	/** 포트원 오류 유형(예: PG_PROVIDER, ALREADY_PAID). 응답이 없으면 null */
	public String getType() { return type; }
}
