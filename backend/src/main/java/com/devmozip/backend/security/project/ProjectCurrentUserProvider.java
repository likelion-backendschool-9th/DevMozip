package com.devmozip.backend.security.project;

/** 실제 회원·구독 결합 전까지 주최자 ID 공급을 격리한다. */
public interface ProjectCurrentUserProvider {
	long getCurrentUserId();
}
