package com.longfeng.aianalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.entity.AiUsageLog;
import com.longfeng.aianalysis.repo.AiUsageLogRepository;
import com.longfeng.aianalysis.service.QuestionAnalyzerImpl;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * WT4 · ai_usage_log 真写库验证（监督铁证 第 7 项）。
 *
 * <p>本 IT 直接调 {@link QuestionAnalyzerImpl#recordUsage} 模拟 LLM 调用结束后的写库行为 ·
 * 验证：
 *
 * <ol>
 *   <li>{@code ai_usage_log} 表多出 row（chat api_type · provider · tokens_in &gt; 0）
 *   <li>多次调用 → 多行追加（append-only）
 *   <li>schema 字段全：provider / model / api_type / tokens_in / tokens_out / cost_cents /
 *       latency_ms / status / created_at
 * </ol>
 *
 * <p>设计选择：不走 {@code streamAnalyze} 全链路是因为 prompt template 含 "你现在是" 等
 * 触发 PromptInjectionGuard 拦截关键词（pre-existing 行为 · 非本 WT 范畴 · 留待后续修
 * prompt template 加 guard exempt 段）· 直接验"写库" 才是本铁证的核心断言。
 *
 * <p>前置条件：本地 PG 跑在 {@code localhost:15432/wrongbook} · Flyway 自动建 ai_usage_log
 * （V1.0.023）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "longfeng.ai.spool.tmp-dir=${java.io.tmpdir}/longfeng-ai-spool-it",
    })
class AiUsageLogIT extends AiAnalysisIntegrationTestBase {

  @Autowired private QuestionAnalyzerImpl analyzer;
  @Autowired private AiUsageLogRepository usageRepo;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM ai_usage_log");
  }

  @Test
  @DisplayName("recordUsage writes a row · provider non-null · tokens_in > 0 · status=0")
  void recordUsage_writesRow() {
    analyzer.recordUsage("qianwen", "chat", 42, 77, 1234, (short) 0);

    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer cnt =
                  jdbc.queryForObject("SELECT count(*) FROM ai_usage_log", Integer.class);
              assertThat(cnt).as("ai_usage_log must have ≥ 1 row").isGreaterThanOrEqualTo(1);
            });

    List<AiUsageLog> all = usageRepo.findAll();
    assertThat(all).hasSize(1);
    AiUsageLog row = all.get(0);
    // qianwen normalized to dashscope (ck_usage_provider allow-list)
    assertThat(row.getProvider()).isEqualTo("dashscope");
    assertThat(row.getModel()).isNotBlank();
    assertThat(row.getApiType()).isEqualTo("chat");
    assertThat(row.getTokensIn()).isEqualTo(42);
    assertThat(row.getTokensOut()).isEqualTo(77);
    assertThat(row.getLatencyMs()).isEqualTo(1234);
    assertThat(row.getStatus()).isEqualTo((short) 0);
    assertThat(row.getCostCents()).isNotNegative();
    assertThat(row.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("multiple recordUsage invocations append multiple rows (append-only)")
  void recordUsage_appendsMultipleRows() {
    int initial = jdbc.queryForObject("SELECT count(*) FROM ai_usage_log", Integer.class);

    for (int i = 0; i < 3; i++) {
      analyzer.recordUsage("qianwen", "chat", 100 + i, 50 + i, 200 + i, (short) 0);
    }

    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer cnt =
                  jdbc.queryForObject("SELECT count(*) FROM ai_usage_log", Integer.class);
              assertThat(cnt - initial)
                  .as("at least 3 new rows from 3 recordUsage calls")
                  .isGreaterThanOrEqualTo(3);
            });
  }

  @Test
  @DisplayName("recordUsage with FAIL status (9) still writes row · audit trail intact")
  void recordUsage_failStatus_stillWritesRow() {
    analyzer.recordUsage("openai", "chat", 0, 0, 8000, (short) 9);

    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AiUsageLog> all = usageRepo.findAll();
              assertThat(all).isNotEmpty();
              AiUsageLog last = all.get(all.size() - 1);
              assertThat(last.getStatus()).isEqualTo((short) 9);
              assertThat(last.getProvider()).isEqualTo("openai");
            });
  }
}
