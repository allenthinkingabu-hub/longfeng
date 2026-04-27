package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.QWrongItem;
import com.longfeng.wrongbook.entity.QWrongItemTag;
import com.longfeng.wrongbook.entity.WrongItem;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WrongItemQueryRepositoryImpl implements WrongItemQueryRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public List<WrongItem> findPage(
      String subject,
      List<Short> statuses,
      String tagCode,
      Long studentId,
      Long cursor,
      int limit) {
    QWrongItem w = QWrongItem.wrongItem;
    QWrongItemTag t = QWrongItemTag.wrongItemTag;
    BooleanBuilder pred = new BooleanBuilder();

    if (subject != null) pred.and(w.subject.eq(subject));
    if (statuses != null && !statuses.isEmpty()) pred.and(w.status.in(statuses));
    if (studentId != null) pred.and(w.studentId.eq(studentId));
    if (cursor != null) pred.and(w.id.lt(cursor));
    if (tagCode != null) {
      pred.and(
          JPAExpressions.selectOne()
              .from(t)
              .where(t.wrongItemId.eq(w.id).and(t.tagCode.eq(tagCode)))
              .exists());
    }

    return new JPAQueryFactory(em)
        .selectFrom(w)
        .where(pred)
        .orderBy(w.id.desc())
        .limit(limit)
        .fetch();
  }
}
