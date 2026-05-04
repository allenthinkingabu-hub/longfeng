package com.longfeng.auth.repository;

import com.longfeng.auth.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** UserAccount JPA repository · auth-service · S7 BUG-LF-09. */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByWechatOpenid(String wechatOpenid);
}
