package com.devmozip.backend.repository;
import java.util.List;
import com.devmozip.backend.entity.MemberRemovalBallot;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRemovalBallotRepository extends JpaRepository<MemberRemovalBallot,Long> {
	List<MemberRemovalBallot> findByVoteIdOrderByIdAsc(long voteId);
}
