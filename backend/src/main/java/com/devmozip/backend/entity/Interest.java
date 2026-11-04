package com.devmozip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "interests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interest {

    // 관심 분야 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 관심 분야 이름 (회원가입 시 이름으로 조회하므로 중복을 막는다)
    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    @Builder
    public Interest(String name) {
        this.name = name;
    }
}
