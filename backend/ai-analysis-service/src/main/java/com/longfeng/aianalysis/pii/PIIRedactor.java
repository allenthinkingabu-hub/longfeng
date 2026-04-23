package com.longfeng.aianalysis.pii;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Replaces phone numbers, mainland Chinese ID card numbers, and the current student's name with
 * literal placeholders before any payload reaches the LLM. Single source of truth for the PII red
 * line (§4.4 arch doc).
 */
@Service
public class PIIRedactor {

  public static final String PLACEHOLDER_PHONE = "{phone}";
  public static final String PLACEHOLDER_IDCARD = "{idcard}";
  public static final String PLACEHOLDER_STUDENT = "{student}";

  private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
  private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");

  private final JdbcTemplate jdbc;

  public PIIRedactor(@Autowired(required = false) JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String redact(String rawText, Long studentId) {
    if (rawText == null || rawText.isEmpty()) {
      return rawText;
    }
    String out = ID_CARD.matcher(rawText).replaceAll(PLACEHOLDER_IDCARD);
    out = PHONE.matcher(out).replaceAll(PLACEHOLDER_PHONE);
    if (studentId != null && jdbc != null) {
      String name = lookupStudentName(studentId);
      if (name != null && !name.isBlank()) {
        out = out.replace(name, PLACEHOLDER_STUDENT);
      }
    }
    return out;
  }

  private String lookupStudentName(Long studentId) {
    try {
      return jdbc.queryForObject(
          "SELECT username FROM user_account WHERE id = ?", String.class, studentId);
    } catch (Exception ignored) {
      return null;
    }
  }
}
