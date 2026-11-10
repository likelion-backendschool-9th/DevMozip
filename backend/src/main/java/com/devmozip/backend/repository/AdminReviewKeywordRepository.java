package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.entity.ReviewKeyword;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 관리자 리뷰 키워드 관리 (WBS 14.9). 저장은 리뷰 담당의 ReviewKeywordRepository를 쓴다. */
public interface AdminReviewKeywordRepository extends Repository<ReviewKeyword, Long> {
	/** 활성·비활성 키워드 전체. ID 오름차순(리뷰 화면의 선택지 순서와 같다). */
	@Query("select k from ReviewKeyword k order by k.id")
	List<ReviewKeyword> findAllOrdered();

	/** 목록 수정끼리 활성 변경이 겹치지 않도록 기존 키워드를 모두 잠근다. 새 이름의 동시 추가는 이름 UNIQUE 제약이 막는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select k from ReviewKeyword k order by k.id")
	List<ReviewKeyword> lockAll();
}
