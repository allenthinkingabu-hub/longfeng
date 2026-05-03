package com.longfeng.reviewplan.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * S6 notification-service Feign · C10 · 4 渠道扇出 · D-MultiPush.
 *
 * <p>fallback = {@link NotificationFeignClientFallback} · Sentinel 熔断降级时写 push_log status=DEAD.
 * url 走 ${notification.url} · 生产由 Nacos 服务发现覆盖.
 */
@FeignClient(
    name = "notification-service",
    url = "${notification.url:http://localhost:18090}",
    fallback = NotificationFeignClientFallback.class)
public interface NotificationFeignClient {

  /** 渠道 1：微信订阅消息 (WX_MP) · 模板 wrong_question_review_v1. */
  @PostMapping("/notifications/wx-mp/send")
  SendResp sendWxMp(@RequestBody SendReq req);

  /** 渠道 2：APP 推送 (Getui/Firebase). */
  @PostMapping("/notifications/app/send")
  SendResp sendApp(@RequestBody SendReq req);

  /** 渠道 3：邮件 (阿里云邮件推送 / SendGrid). */
  @PostMapping("/notifications/email/send")
  SendResp sendEmail(@RequestBody SendReq req);

  /** 渠道 4：短信 (阿里云短信 / Twilio). */
  @PostMapping("/notifications/sms/send")
  SendResp sendSms(@RequestBody SendReq req);

  /**
   * 推送请求体 · C8 msgkey: 前缀.
   *
   * @param msgkey    C8 幂等键 · "msgkey:" + idempotencyKey
   * @param studentId 学生 ID
   * @param nodeId    review_node ID
   * @param subject   科目（微信模板 thing1）
   * @param errorSummary 错因摘要 ≤ 12 字（微信模板 thing2）
   * @param reviewTime 复习时间描述（微信模板 time3）
   * @param deeplink  跳转深链 wb://review/exec/{nid}
   */
  record SendReq(
      String msgkey,
      Long studentId,
      Long nodeId,
      String subject,
      String errorSummary,
      String reviewTime,
      String deeplink) {}

  /**
   * 推送响应体.
   *
   * @param success   是否送达
   * @param requestId 第三方平台请求 ID（微信 msgid / APNs uuid 等）
   * @param errorCode 错误码（成功时为 null）
   * @param errorMsg  错误描述（成功时为 null）
   */
  record SendResp(boolean success, String requestId, String errorCode, String errorMsg) {}
}
