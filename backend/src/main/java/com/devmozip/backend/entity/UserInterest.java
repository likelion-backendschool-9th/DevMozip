package com.devmozip.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// 같은 회원에게 같은 관심 분야가 중복 등록되지 않게 한다. (회원 한 명이 여러 관심 분야를 갖는 것은 가능하다)
@Table(name = "user_interests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "interest_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterest {

    // 사용자 관심 분야 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 ID (FK)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private Member member;

    // 관심 분야 ID (FK)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interest_id")
    private Interest interest;

    @Builder
    public UserInterest(Member member, Interest interest) {
        this.member = member;
        this.interest = interest;
    }
}
