package com.budgetops.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import javax.management.relation.Role;

@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true) // Google sub
    private String providerId;

    @Column (unique = true)
    private String email;

    private String name;
    private String picture;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role { USER, ADMIN }
}
