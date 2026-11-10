package com.devmozip.backend.repository;
import java.util.*;
import com.devmozip.backend.entity.MemberRemovalVote;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRemovalVoteRepository extends JpaRepository<MemberRemovalVote,Long> {
	List<MemberRemovalVote> findByProjectIdAndStatus(long projectId,MemberRemovalVote.Status status);
	List<MemberRemovalVote> findTop30ByProjectIdOrderByIdDesc(long projectId);
	Optional<MemberRemovalVote> findByIdAndProjectId(long id,long projectId);
}
