package com.budgetops.backend.oauth.repository;

//import com.budgetops.backend.domain.entity.User;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.Optional;
//
//public interface UserRepository extends JpaRepository<User, Long> {
//    Optional<User> findByProvider(String providerId);
//    Optional<User> findByEmail(String email);
//}

import com.budgetops.backend.oauth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}