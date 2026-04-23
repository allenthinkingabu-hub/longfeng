package com.longfeng.wrongbook.dto;

import java.util.List;

public record WrongItemPageVO(List<WrongItemVO> list, String nextCursor) {}
