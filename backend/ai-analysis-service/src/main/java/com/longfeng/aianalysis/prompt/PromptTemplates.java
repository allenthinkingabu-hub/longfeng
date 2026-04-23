package com.longfeng.aianalysis.prompt;

import java.util.Map;

/**
 * Prompt registry · maps subject + prompt version label → prompt body. Version label is tied to an
 * INT code that lands in {@code wrong_item_analysis.version} (Q2-R1 decision · arch §4.3).
 */
public final class PromptTemplates {

  public static final String VERSION_LABEL = "prompt-v1.0";
  public static final int VERSION_CODE = 1;

  private static final Map<String, String> BY_SUBJECT =
      Map.ofEntries(
          Map.entry(
              "math",
              """
              你是一名初中数学讲解助手。请基于以下错题给出讲解、错因、自动标签。
              输出 JSON：{"explain": string(<=600字), "causeTag": "CONCEPT|CALCULATION|COMPREHENSION|HANDWRITING|OTHER", "autoTags": string[](<=5)}
              错题：
              subject={{subject}}
              stem={{stemText}}
              """),
          Map.entry(
              "physics",
              """
              你是一名初中物理讲解助手。按上述 JSON schema 输出讲解 + 错因 + 标签。
              stem={{stemText}}
              """),
          Map.entry(
              "chemistry",
              """
              你是一名初中化学讲解助手。按上述 JSON schema 输出讲解 + 错因 + 标签。
              stem={{stemText}}
              """),
          Map.entry(
              "english",
              """
              You are a secondary-school English tutor. Output JSON {explain, causeTag, autoTags}.
              stem={{stemText}}
              """),
          Map.entry(
              "chinese",
              """
              你是一名初中语文讲解助手。按上述 JSON schema 输出讲解 + 错因 + 标签。
              stem={{stemText}}
              """));

  private PromptTemplates() {}

  public static String render(String subject, String sanitizedStem) {
    String tpl = BY_SUBJECT.getOrDefault(subject, BY_SUBJECT.get("math"));
    return tpl.replace("{{subject}}", subject).replace("{{stemText}}", sanitizedStem);
  }

  public static String versionLabel() {
    return VERSION_LABEL;
  }

  public static int versionCode() {
    return VERSION_CODE;
  }
}
