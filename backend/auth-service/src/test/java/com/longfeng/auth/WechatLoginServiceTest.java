package com.longfeng.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.longfeng.auth.dto.WechatLoginReq;
import com.longfeng.auth.dto.WechatLoginResp;
import com.longfeng.auth.entity.UserAccount;
import com.longfeng.auth.entity.UserToken;
import com.longfeng.auth.repository.UserAccountRepository;
import com.longfeng.auth.repository.UserTokenRepository;
import com.longfeng.auth.service.WechatLoginService;
import com.longfeng.auth.util.JwtUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for WechatLoginService · happy path · S7 BUG-LF-09.
 *
 * <p>Verifies: dev_code_alice → openid_alice → user_account UPSERT → JWT sign.
 */
@ExtendWith(MockitoExtension.class)
class WechatLoginServiceTest {

  @Mock
  private UserAccountRepository userAccountRepository;

  @Mock
  private UserTokenRepository userTokenRepository;

  @Mock
  private JwtUtils jwtUtils;

  private WechatLoginService service;

  @BeforeEach
  void setUp() {
    service = new WechatLoginService(userAccountRepository, userTokenRepository, jwtUtils);
  }

  @Test
  void happyPath_newUser_devCodeAlice() {
    // Given: no existing user for openid_alice
    when(userAccountRepository.findByWechatOpenid("openid_alice")).thenReturn(Optional.empty());

    UserAccount saved = new UserAccount();
    saved.setId(123456789L);
    saved.setUsername("wx_nid_alice");
    saved.setWechatOpenid("openid_alice");
    saved.setRole("STUDENT");
    saved.setStatus((short) 1);
    saved.setCreatedAt(Instant.now());
    saved.setUpdatedAt(Instant.now());
    when(userAccountRepository.save(any(UserAccount.class))).thenReturn(saved);

    when(jwtUtils.sign(any(Long.class), anyString(), anyString(), any(Instant.class)))
        .thenReturn("eyJhbGciOiJSUzI1NiJ9.stub.signature");

    UserToken savedToken = new UserToken();
    savedToken.setId(1L);
    when(userTokenRepository.save(any(UserToken.class))).thenReturn(savedToken);

    WechatLoginReq req = new WechatLoginReq("dev_code_alice", "fp_test_001", true);

    // When
    WechatLoginResp resp = service.handleLogin(req);

    // Then
    assertThat(resp.accessToken()).isEqualTo("eyJhbGciOiJSUzI1NiJ9.stub.signature");
    assertThat(resp.studentId()).isEqualTo("123456789");
    assertThat(resp.isNewUser()).isTrue();
    assertThat(resp.refreshToken()).isEmpty();
    assertThat(resp.expiresAt()).isAfter(Instant.now());
    assertThat(resp.expiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));

    verify(userAccountRepository).save(any(UserAccount.class));
    verify(userTokenRepository).save(any(UserToken.class));
    verify(jwtUtils).sign(any(Long.class), anyString(), anyString(), any(Instant.class));
  }

  @Test
  void happyPath_existingUser_devCodeBob() {
    // Given: existing user for openid_bob
    UserAccount existing = new UserAccount();
    existing.setId(987654321L);
    existing.setUsername("wx_bob");
    existing.setWechatOpenid("openid_bob");
    existing.setRole("STUDENT");
    existing.setStatus((short) 1);
    existing.setCreatedAt(Instant.now());
    existing.setUpdatedAt(Instant.now());
    when(userAccountRepository.findByWechatOpenid("openid_bob")).thenReturn(Optional.of(existing));

    when(jwtUtils.sign(any(Long.class), anyString(), anyString(), any(Instant.class)))
        .thenReturn("eyJhbGciOiJSUzI1NiJ9.bob.signature");

    UserToken savedToken = new UserToken();
    savedToken.setId(2L);
    when(userTokenRepository.save(any(UserToken.class))).thenReturn(savedToken);

    WechatLoginReq req = new WechatLoginReq("dev_code_bob", "fp_test_002", true);

    // When
    WechatLoginResp resp = service.handleLogin(req);

    // Then
    assertThat(resp.accessToken()).isEqualTo("eyJhbGciOiJSUzI1NiJ9.bob.signature");
    assertThat(resp.studentId()).isEqualTo("987654321");
    assertThat(resp.isNewUser()).isFalse();
  }

  @Test
  void verifyWechatCode_invalidCode_throws() {
    assertThatThrownBy(() -> service.verifyWechatCode("invalid_code"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid wx_code");
  }

  @Test
  void verifyWechatCode_devCodeAlice_returnsOpenid() {
    String[] result = service.verifyWechatCode("dev_code_alice");
    assertThat(result[0]).isEqualTo("openid_alice");
    assertThat(result[1]).isEqualTo("unionid_alice");
  }

  @Test
  void verifyWechatCode_devCodeBob_returnsOpenid() {
    String[] result = service.verifyWechatCode("dev_code_bob");
    assertThat(result[0]).isEqualTo("openid_bob");
    assertThat(result[1]).isEqualTo("unionid_bob");
  }
}
