package com.devmozip.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobCategory {
    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
