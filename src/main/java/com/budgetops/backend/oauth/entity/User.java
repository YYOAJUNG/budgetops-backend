package com.budgetops.backend.oauth.entity;

import jakarta.persistence.*;

import java.time.Instant;

//import jakarta.persistence.*;
//import lombok.*;
//
//import javax.management.relation.Role;
//
//@Entity @Table(name = "users")
//@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
//public class User {
//    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @Column(unique = true) // Google sub
//    private String providerId;
//
//    @Column (unique = true)
//    private String email;
//
//    private String name;
//    private String picture;
//
//    @Enumerated(EnumType.STRING)
//    private Role role;
//
//    public enum Role { USER, ADMIN }
//}
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 191)
    private String email;

    @Column(length = 100)
    private String name;

    private String picture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public User() {}

    public User(String email, String name, String picture) {
        this.email = email;
        this.name = name;
        this.picture = picture;
    }

    // getters and setters
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPicture() { return picture; }
    public void setPicture(String picture) { this.picture = picture; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
}