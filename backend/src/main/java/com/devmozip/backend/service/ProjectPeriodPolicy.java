package com.devmozip.backend.service;

import java.time.LocalDate;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.enums.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** #47 상태·기간 API에서도 동일한 종료일 검증을 재사용한다. */
@Component
public class ProjectPeriodPolicy {
	public void validate(CreateProjectRequest input) {
		if (input.recruitmentStart().isAfter(input.recruitmentEnd())
			|| input.developmentStart().isAfter(input.developmentEnd())
			|| input.recruitmentEnd().isAfter(input.developmentEnd())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모집·개발 날짜 순서를 확인해 주세요.");
		}
	}

	public void validateUpdate(Project project, CreateProjectRequest input) {
		validate(input);
		if (input.recruitmentEnd().isBefore(project.getRecruitmentEnd())
			|| input.developmentEnd().isBefore(project.getDevelopmentEnd())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "모집·개발 종료일은 기존 종료일보다 앞당길 수 없습니다.");
		}
		if (project.getStatus() == ProjectStatus.IN_PROGRESS
			&& (!input.recruitmentStart().equals(project.getRecruitmentStart())
				|| !input.developmentStart().equals(project.getDevelopmentStart()))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 프로젝트의 시작일은 변경할 수 없습니다.");
		}
		// 모집 마감일이 이미 지난 공고의 일반 내용 수정은 허용한다.
	}
}
