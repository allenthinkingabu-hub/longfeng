package com.longfeng.reviewplan.job;

import com.longfeng.reviewplan.entity.PushLog;
import com.longfeng.reviewplan.entity.PushTask;
import com.longfeng.reviewplan.feign.NotificationFeignClient;
import com.longfeng.reviewplan.feign.NotificationFeignClient.SendReq;
import com.longfeng.reviewplan.feign.NotificationFeignClient.SendResp;
import com.longfeng.reviewplan.repo.PushLogRepository;
import com.longfeng.reviewplan.repo.PushTaskRepository;
import com.longfeng.reviewplan.support.SnowflakeIdGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 推送任务中继 Job · S6 BE-10 · TDD §9 + D-MultiPush.
 *
 * <p>周期 30s · 扫 wb_push_task status=PENDING AND scheduled_at<=now()
 * · 多通道 fallback chain (微信优先 → APP → EMAIL → SMS → INAPP 红点)
 * · CAS UPDATE status · 防并发双推
 * · 写 wb_push_log（每通道一行）
 *
 * <p>条件启用 {@code review.job.enabled=true} · IT/local 默认关.
 */
@Component
@ConditionalOnProperty(value = "review.job.enabled", havingValue = "true", matchIfMissing = false)
public class PushTaskRelayJob {

  private static final Logger LOG = LoggerFactory.getLogger(PushTaskRelayJob.class);
  private static final int BATCH_SIZE = 200;

  /** D-MultiPush 渠道优先级. */
  private static final List<String> CHANNEL_PRIORITY =
      Arrays.asList("WX_MP", "APP", "EMAIL", "SMS");

  /** C8 msgkey: 前缀. */
  private static final String MSGKEY_PREFIX = "msgkey:";

  private final PushTaskRepository pushTaskRepo;
  private final PushLogRepository pushLogRepo;
  private final NotificationFeignClient notificationClient;
  private final SnowflakeIdGenerator idGenerator;
  private final TransactionTemplate txTemplate;

  private final Counter scanCounter;
  private final Counter deliveredCounter;
  private final Counter fallbackCounter;
  private final Counter deadCounter;

  @Autowired
  public PushTaskRelayJob(
      PushTaskRepository pushTaskRepo,
      PushLogRepository pushLogRepo,
      NotificationFeignClient notificationClient,
      SnowflakeIdGenerator idGenerator,
      PlatformTransactionManager txManager,
      MeterRegistry meterRegistry) {
    this.pushTaskRepo = pushTaskRepo;
    this.pushLogRepo = pushLogRepo;
    this.notificationClient = notificationClient;
    this.idGenerator = idGenerator;
    this.txTemplate = new TransactionTemplate(txManager);
    this.scanCounter = Counter.builder("push_task_relay_scan_total").register(meterRegistry);
    this.deliveredCounter =
        Counter.builder("notification_push_delivered_total").register(meterRegistry);
    this.fallbackCounter =
        Counter.builder("notification_push_fallback_total").register(meterRegistry);
    this.deadCounter = Counter.builder("notification_push_dead_total").register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${review.push.relay-interval-ms:30000}")
  public void relay() {
    execute();
  }

  /** 业务入口（IT 直接调此方法 · 不经 @Scheduled）. */
  public int execute() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<PushTask> batch =
        pushTaskRepo.findPending(now, PageRequest.of(0, BATCH_SIZE));
    scanCounter.increment();

    int delivered = 0;
    for (PushTask task : batch) {
      Boolean ok = txTemplate.execute(status -> processOne(task));
      if (Boolean.TRUE.equals(ok)) {
        delivered++;
      }
    }
    if (!batch.isEmpty()) {
      LOG.info("push-relay · scanned={} · delivered={}", batch.size(), delivered);
    }
    return delivered;
  }

  /**
   * 处理单个推送任务 · 在独立事务内执行 · 返回 true=送达.
   */
  private boolean processOne(PushTask task) {
    // CAS 抢占：将 status 从 PENDING(0) → PROCESSING(1)
    int rows =
        pushTaskRepo.updateStatusCas(
            task.getId(), task.getVersion(), PushTask.STATUS_PROCESSING, null);
    if (rows != 1) {
      LOG.debug("push-relay CAS skip · taskId={} (并发抢占)", task.getId());
      return false;
    }

    // D-MultiPush 串行尝试各渠道
    List<String> channels =
        task.getChannels() != null
            ? Arrays.asList(task.getChannels().split(","))
            : CHANNEL_PRIORITY;

    SendReq req = buildSendReq(task);
    boolean delivered = false;
    String lastError = null;

    for (String channel : CHANNEL_PRIORITY) {
      if (!channels.contains(channel)) continue;

      SendResp resp = callChannel(channel, req);
      writePushLog(task, channel, resp);

      if (resp.success()) {
        delivered = true;
        deliveredCounter.increment();
        LOG.info(
            "push-relay delivered · taskId={} · channel={} · requestId={}",
            task.getId(), channel, resp.requestId());
        break;
      } else {
        fallbackCounter.increment();
        lastError = channel + ":" + resp.errorCode();
        LOG.warn(
            "push-relay channel fail · taskId={} · channel={} · error={}",
            task.getId(), channel, resp.errorCode());
      }
    }

    // 全部失败 → 站内红点兜底 + status=DEAD
    short finalStatus;
    if (delivered) {
      finalStatus = PushTask.STATUS_SUCCESS;
    } else {
      // 写站内红点（INAPP 永远兜底）
      SendResp inappResp =
          new SendResp(true, "inapp-" + task.getId(), null, null);
      writePushLog(task, "INAPP", inappResp);
      finalStatus = PushTask.STATUS_DEAD;
      deadCounter.increment();
      LOG.warn("push-relay all channels failed · taskId={} · degraded to INAPP", task.getId());
    }

    // 更新最终状态（version+1 已被 CAS 占掉，此处直接按 id 更新）
    final String finalLastError = lastError;
    final short finalFinalStatus = finalStatus;
    txTemplate.execute(
        status ->
            pushTaskRepo.updateStatusCas(
                task.getId(),
                task.getVersion() + 1, // CAS 后 version 已+1
                finalFinalStatus,
                finalLastError));

    return delivered;
  }

  /**
   * 按渠道调用对应 Feign 方法 · C10 Sentinel fallback 由 @FeignClient fallback 自动处理.
   */
  private SendResp callChannel(String channel, SendReq req) {
    try {
      return switch (channel) {
        case "WX_MP" -> notificationClient.sendWxMp(req);
        case "APP"   -> notificationClient.sendApp(req);
        case "EMAIL" -> notificationClient.sendEmail(req);
        case "SMS"   -> notificationClient.sendSms(req);
        default -> new SendResp(false, null, "UNKNOWN_CHANNEL", "channel=" + channel);
      };
    } catch (Exception e) {
      LOG.warn("push-relay Feign exception · channel={} · msg={}", channel, e.getMessage());
      return new SendResp(false, null, "FEIGN_ERROR", e.getMessage());
    }
  }

  /** 构建 SendReq · C8: msgkey: 前缀. */
  private SendReq buildSendReq(PushTask task) {
    return new SendReq(
        MSGKEY_PREFIX + task.getIdempotencyKey(), // C8
        task.getStudentId(),
        task.getNodeId(),
        null,   // subject · 由 notification-service 从 DB 查取
        null,   // errorSummary · 同上
        null,   // reviewTime · 同上
        "wb://review/exec/" + task.getNodeId());
  }

  /** 写 push_log（每通道一行）. */
  private void writePushLog(PushTask task, String channel, SendResp resp) {
    PushLog log = new PushLog();
    log.setId(idGenerator.nextId());
    log.setTaskId(task.getId());
    log.setChannel(channel);
    log.setRequestId(resp.requestId());
    log.setSuccess(resp.success());
    log.setErrorCode(resp.errorCode());
    log.setErrorMsg(resp.errorMsg());
    log.setTenantId(task.getTenantId() != null ? task.getTenantId() : 1L);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    log.setCreatedAt(now);
    if (resp.success()) {
      log.setDeliveredAt(now);
    }
    pushLogRepo.save(log);
  }
}
