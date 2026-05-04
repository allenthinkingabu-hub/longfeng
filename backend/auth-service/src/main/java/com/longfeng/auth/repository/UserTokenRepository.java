package com.longfeng.auth.repository;

import com.longfeng.auth.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** UserToken JPA repository · auth-service · S7 BUG-LF-09. */
@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {}
