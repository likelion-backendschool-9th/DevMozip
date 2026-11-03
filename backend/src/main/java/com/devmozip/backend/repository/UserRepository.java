package com.devmozip.backend.repository;

import com.devmozip.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "jobCategory")
    Optional<User> findByTagAndStatus(String tag, String status);
}
