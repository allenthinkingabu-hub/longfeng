package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.service.dto.AiModelInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D-AI-Model-Catalog · {@link AiModelsController} 单元测试（plan §5.S3 SC-16 + 出口门禁）。
 *
 * <p>不依赖 Spring Context（纯 POJO 测试）· F-02/F-05 教训：直接 new controller。
 *
 * <p>验收标准：
 * <ul>
 *   <li>NORMAL tier → 只返 vipOnly=false 的模型（qwen-vl-max）
 *   <li>VIP tier → 返 qwen-vl-max + gpt-4o-mini + glm-4v（不含 costTier=H 的 claude-opus）
 *   <li>VIP_PLUS tier → 返全部 4 模型（含 claude-opus）
 *   <li>所有模型含 D-AI-Model-Catalog 全字段（vipOnly + supportedSubjects + costTier）
 * </ul>
 */
class AiModelsControllerTest {

  private final AiModelsController controller = new AiModelsController();

  @Test
  void normal_tier_seesOnlyFreeModels() {
    List<AiModelInfo> result = controller.listModels("NORMAL");

    assertThat(result)
        .as("NORMAL tier 只能看 vipOnly=false 的模型")
        .allSatisfy(m -> assertThat(m.vipOnly()).isFalse());
    assertThat(result).extracting(AiModelInfo::id).contains("qianwen:qwen-vl-max");
    assertThat(result).extracting(AiModelInfo::id)
        .doesNotContain("openai:gpt-4o-mini", "zhipu:glm-4v", "openai:claude-opus");
  }

  @Test
  void vip_tier_seesVipButNotPlusTier() {
    List<AiModelInfo> result = controller.listModels("VIP");

    assertThat(result).extracting(AiModelInfo::id)
        .contains("qianwen:qwen-vl-max", "openai:gpt-4o-mini", "zhipu:glm-4v");
    // claude-opus costTier=H · VIP 看不到
    assertThat(result).extracting(AiModelInfo::id)
        .doesNotContain("openai:claude-opus");
  }

  @Test
  void vip_plus_tier_seesAllModels() {
    List<AiModelInfo> result = controller.listModels("VIP_PLUS");

    assertThat(result).hasSize(4);
    assertThat(result).extracting(AiModelInfo::id)
        .containsExactlyInAnyOrder(
            "qianwen:qwen-vl-max",
            "openai:gpt-4o-mini",
            "zhipu:glm-4v",
            "openai:claude-opus");
  }

  @Test
  void allModels_haveRequiredDaiModelCatalogFields() {
    // VIP_PLUS 看全部 · 检查所有字段非空
    List<AiModelInfo> result = controller.listModels("VIP_PLUS");

    assertThat(result).isNotEmpty();
    assertThat(result).allSatisfy(m -> {
      assertThat(m.id()).as("id 不为空").isNotBlank();
      assertThat(m.provider()).as("provider 不为空").isNotBlank();
      assertThat(m.displayName()).as("displayName 不为空").isNotBlank();
      assertThat(m.supportedSubjects()).as("supportedSubjects 不为空").isNotEmpty();
      assertThat(m.costTier()).as("costTier 不为空 · L/M/H").isIn("L", "M", "H");
      assertThat(m.avgLatencyMs()).as("avgLatencyMs > 0").isGreaterThan(0);
      assertThat(m.notes()).as("notes 不为空").isNotBlank();
    });
  }

  @Test
  void defaultTier_unknownValue_treatedAsNormal() {
    // 未知 tier 值 → 只返免费模型（NORMAL 路径）
    List<AiModelInfo> result = controller.listModels("UNKNOWN");

    assertThat(result)
        .as("未知 tier 按 NORMAL 处理 · 只看 vipOnly=false")
        .allSatisfy(m -> assertThat(m.vipOnly()).isFalse());
  }

  @Test
  void catalog_contains4StubModels_withCorrectProviders() {
    List<AiModelInfo> result = controller.listModels("VIP_PLUS");

    assertThat(result).extracting(AiModelInfo::provider)
        .containsExactlyInAnyOrder("qianwen", "openai", "zhipu", "openai");
  }
}
