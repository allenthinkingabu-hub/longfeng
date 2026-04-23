package com.longfeng.wrongbook.service;

import com.longfeng.wrongbook.dto.CreateAttemptReq;
import com.longfeng.wrongbook.dto.WrongAttemptVO;
import com.longfeng.wrongbook.entity.WrongAttempt;
import com.longfeng.wrongbook.entity.WrongItem;
import com.longfeng.wrongbook.repo.WrongAttemptRepository;
import com.longfeng.wrongbook.repo.WrongItemRepository;
import com.longfeng.wrongbook.support.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** wrong_attempt append-only aggregate (Q4-R1 drift resolution). */
@Service
public class WrongAttemptService {

  private final WrongAttemptRepository attemptRepo;
  private final WrongItemRepository itemRepo;
  private final SnowflakeIdGenerator ids;

  public WrongAttemptService(
      WrongAttemptRepository attemptRepo,
      WrongItemRepository itemRepo,
      SnowflakeIdGenerator ids) {
    this.attemptRepo = attemptRepo;
    this.itemRepo = itemRepo;
    this.ids = ids;
  }

  @Transactional
  public WrongAttemptVO create(Long wrongItemId, CreateAttemptReq req) {
    WrongItem item =
        itemRepo
            .findById(wrongItemId)
            .orElseThrow(
                () ->
                    new WrongItemService.NotFoundException(
                        "wrong_item not found: " + wrongItemId));
    WrongAttempt e = new WrongAttempt();
    e.setId(ids.nextId());
    e.setWrongItemId(item.getId());
    e.setStudentId(req.studentId());
    e.setAnswerText(req.answerText());
    e.setCorrect(req.isCorrect());
    e.setDurationSec(req.durationSec());
    e.setClientSource(req.clientSource());
    e.setSubmittedAt(Instant.now());
    WrongAttempt saved = attemptRepo.save(e);
    return toVo(saved);
  }

  @Transactional(readOnly = true)
  public List<WrongAttemptVO> list(Long wrongItemId, int size) {
    int capped = Math.min(Math.max(size, 1), 100);
    return attemptRepo
        .findByWrongItemIdOrderBySubmittedAtDescIdDesc(wrongItemId, Limit.of(capped))
        .stream()
        .map(this::toVo)
        .toList();
  }

  private WrongAttemptVO toVo(WrongAttempt a) {
    return new WrongAttemptVO(
        a.getId(),
        a.getWrongItemId(),
        a.getStudentId(),
        a.getAnswerText(),
        a.getCorrect(),
        a.getDurationSec(),
        a.getClientSource(),
        a.getSubmittedAt());
  }
}
