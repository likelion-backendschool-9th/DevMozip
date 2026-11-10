package com.devmozip.backend.service;

import java.util.Set;

/** #48 연결 경계. 대기·승인 상태 지원자가 있는 모집 직군을 반환한다.
 * 어댑터는 호출자가 보유한 프로젝트 잠금과 같은 트랜잭션에서 읽어야 한다. */
public interface ProjectApplicationUsage {
	Set<String> usedRoles(long projectId);
}
