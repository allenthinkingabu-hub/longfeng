package com.longfeng.auth.controller;

import com.longfeng.auth.dto.WechatLoginReq;
import com.longfeng.auth.dto.WechatLoginResp;
import com.longfeng.auth.service.WechatLoginService;
import com.longfeng.common.dto.ApiResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeChat login endpoint · S7 BUG-LF-09.
 *
 * <p>POST /api/auth/wechat-login · accepts WechatLoginReq · returns WechatLoginResp wrapped in ApiResult.
 */
@RestController
public class WechatLoginController {

  private static final Logger log = LoggerFactory.getLogger(WechatLoginController.class);

  private final WechatLoginService wechatLoginService;

  public WechatLoginController(WechatLoginService wechatLoginService) {
    this.wechatLoginService = wechatLoginService;
  }

  /**
   * WeChat login endpoint.
   *
   * @param req validated request with wx_code, device_fp, consent_accepted
   * @return 200 with access_token JWT on success
   */
  @PostMapping("/api/auth/wechat-login")
  public ResponseEntity<ApiResult<WechatLoginResp>> wechatLogin(
      @Valid @RequestBody WechatLoginReq req) {
    log.info("wechat-login request wx_code={} device_fp={}", req.wxCode(), req.deviceFp());
    WechatLoginResp resp = wechatLoginService.handleLogin(req);
    log.info("wechat-login success student_id={} is_new_user={}", resp.studentId(), resp.isNewUser());
    return ResponseEntity.ok(ApiResult.ok(resp));
  }
}
