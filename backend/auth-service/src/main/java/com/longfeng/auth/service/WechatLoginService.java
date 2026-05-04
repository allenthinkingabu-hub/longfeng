package com.longfeng.auth.service;

import com.longfeng.auth.dto.WechatLoginReq;
import com.longfeng.auth.dto.WechatLoginResp;
import com.longfeng.auth.entity.UserAccount;
import com.longfeng.auth.entity.UserToken;
import com.longfeng.auth.repository.UserAccountRepository;
import com.longfeng.auth.repository.UserTokenRepository;
import com.longfeng.auth.util.JwtUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WeChat login service · dev-profile stub only · S7 BUG-LF-09.
 *
 * <p>Flow:
 * <ol>
 *   <li>verifyWechatCode(wx_code) — dev stub: maps dev_code_* to fixed openids
 *   <li>UPSERT user_account by wechat_openid
 *   <li>Sign RS256 JWT
 *   <li>INSERT user_token (token_hash = SHA-256)
 *   <li>Return WechatLoginResp
 * </ol>
 */
@Service
public class WechatLoginService {

  private static final Logger log = LoggerFactory.getLogger(WechatLoginService.class);

  /** Dev-profile stub mapping: wx_code → {openid, unionid}. */
  private static final Map<String, String[]> DEV_CODE_MAP = Map.of(
      "dev_code_alice", new String[]{"openid_alice", "unionid_alice"},
      "dev_code_bob", new String[]{"openid_bob", "unionid_bob"}
  );

  private final UserAccountRepository userAccountRepository;
  private final UserTokenRepository userTokenRepository;
  private final JwtUtils jwtUtils;

  public WechatLoginService(
      UserAccountRepository userAccountRepository,
      UserTokenRepository userTokenRepository,
      JwtUtils jwtUtils) {
    this.userAccountRepository = userAccountRepository;
    this.userTokenRepository = userTokenRepository;
    this.jwtUtils = jwtUtils;
  }

  /**
   * Handles WeChat login request end-to-end.
   *
   * @param req validated login request
   * @return login response with JWT tokens and user info
   */
  @Transactional
  public WechatLoginResp handleLogin(WechatLoginReq req) {
    // Step 1: resolve wx_code → openid (dev stub)
    String[] wxInfo = verifyWechatCode(req.wxCode());
    String openid = wxInfo[0];
    String unionid = wxInfo[1];

    // Step 2: UPSERT user_account
    Optional<UserAccount> existing = userAccountRepository.findByWechatOpenid(openid);
    boolean isNewUser = existing.isEmpty();
    UserAccount user;
    if (isNewUser) {
      user = new UserAccount();
      // Snowflake-style ID: use timestamp + random (simplified for dev)
      user.setId(generateId());
      user.setUsername("wx_" + openid.substring(Math.max(0, openid.length() - 10)));
      user.setWechatOpenid(openid);
      user.setWechatUnionid(unionid);
      user.setRole("STUDENT");
      user.setStatus((short) 1);
      user.setTimezone("Asia/Shanghai");
      user.setCreatedAt(Instant.now());
      user.setUpdatedAt(Instant.now());
      user = userAccountRepository.save(user);
      log.info("created new user id={} openid={}", user.getId(), openid);
    } else {
      user = existing.get();
      log.info("existing user id={} openid={}", user.getId(), openid);
    }

    // Step 3: Sign RS256 JWT (+24h)
    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
    String accessToken = jwtUtils.sign(user.getId(), user.getRole(), req.deviceFp(), expiresAt);

    // Step 4: INSERT user_token
    UserToken tokenRecord = new UserToken();
    tokenRecord.setUserId(user.getId());
    tokenRecord.setTokenHash(sha256Hex(accessToken));
    tokenRecord.setDeviceFpHash(sha256Hex(req.deviceFp()));
    tokenRecord.setIssuedAt(Instant.now());
    tokenRecord.setExpiresAt(expiresAt);
    userTokenRepository.save(tokenRecord);

    // Step 5: return response
    return new WechatLoginResp(
        accessToken,
        "",  // refresh token not implemented this sprint
        user.getId().toString(),
        isNewUser,
        expiresAt
    );
  }

  /**
   * Dev-profile stub: validates wx_code and returns [openid, unionid].
   *
   * @param wxCode the wx.login code from miniprogram
   * @return [openid, unionid]
   * @throws IllegalArgumentException for unknown dev codes
   */
  public String[] verifyWechatCode(String wxCode) {
    String[] info = DEV_CODE_MAP.get(wxCode);
    if (info == null) {
      throw new IllegalArgumentException(
          "invalid wx_code · only dev_code_* supported in dev: " + wxCode);
    }
    return info;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Generates a simple unique ID for dev use (not Snowflake, just timestamp + random).
   * Production should use Snowflake worker ID from config.
   */
  private static long generateId() {
    // epoch millis shifted left 20 bits + 20 random bits
    long ts = System.currentTimeMillis();
    int rnd = ThreadLocalRandom.current().nextInt(1 << 20);
    return (ts << 20) | rnd;
  }
}
