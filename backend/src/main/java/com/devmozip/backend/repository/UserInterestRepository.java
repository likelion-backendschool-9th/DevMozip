package com.devmozip.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
}
