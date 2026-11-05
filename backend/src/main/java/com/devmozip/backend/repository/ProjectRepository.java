package com.devmozip.backend.repository;

import java.util.Optional;
import com.devmozip.backend.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
	Optional<Project> findByIdAndDeletedAtIsNull(long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Project p where p.id=:id and p.deletedAt is null")
	Optional<Project> findLiveForUpdate(@Param("id") long id);
}
