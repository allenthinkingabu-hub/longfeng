package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.WrongItem;
import java.util.List;

public interface WrongItemQueryRepository {
  List<WrongItem> findPage(
      String subject,
      List<Short> statuses,
      String tagCode,
      Long studentId,
      Long cursor,
      int limit);
}
