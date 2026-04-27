package com.longfeng.wrongbook.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkTagReq(@NotNull List<String> tags) {}
