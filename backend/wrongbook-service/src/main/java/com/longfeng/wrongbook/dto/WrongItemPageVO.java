package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Cursor-paginated list response.
 *
 * <p>S7 contract fixes:
 * <ul>
 *   <li>Issue 1: {@code list} → {@code items}
 *   <li>Issue 2: {@code nextCursor} → {@code next_cursor} (snake_case) + added {@code has_more}
 * </ul>
 */
public record WrongItemPageVO(
    List<WrongItemVO> items,
    @JsonProperty("next_cursor") String nextCursor,
    @JsonProperty("has_more") boolean hasMore) {}
