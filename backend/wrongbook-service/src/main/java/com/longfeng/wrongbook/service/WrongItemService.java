package com.longfeng.wrongbook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.domain.WrongItemStatus;
import com.longfeng.wrongbook.dto.BulkTagReq;
import com.longfeng.wrongbook.dto.ConfirmImageReq;
import com.longfeng.wrongbook.dto.CreateWrongItemReq;
import com.longfeng.wrongbook.dto.SetDifficultyReq;
import com.longfeng.wrongbook.dto.UpdateWrongItemReq;
import com.longfeng.wrongbook.dto.WrongItemImageVO;
import com.longfeng.wrongbook.dto.WrongItemPageVO;
import com.longfeng.wrongbook.dto.WrongItemTagVO;
import com.longfeng.wrongbook.dto.WrongItemVO;
import com.longfeng.wrongbook.entity.AuditLog;
import com.longfeng.wrongbook.entity.TagTaxonomy;
import com.longfeng.wrongbook.entity.WrongItem;
import com.longfeng.wrongbook.entity.WrongItemImage;
import com.longfeng.wrongbook.entity.WrongItemTag;
import com.longfeng.wrongbook.event.WrongItemChangedEvent;
import com.longfeng.wrongbook.mq.WrongItemProducer;
import com.longfeng.wrongbook.repo.AuditLogRepository;
import com.longfeng.wrongbook.repo.TagTaxonomyRepository;
import com.longfeng.wrongbook.repo.WrongItemImageRepository;
import com.longfeng.wrongbook.repo.WrongItemRepository;
import com.longfeng.wrongbook.repo.WrongItemTagRepository;
import com.longfeng.wrongbook.support.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WrongItemService {

  private final WrongItemRepository itemRepo;
  private final WrongItemTagRepository tagRepo;
  private final WrongItemImageRepository imageRepo;
  private final TagTaxonomyRepository taxonomyRepo;
  private final AuditLogRepository auditRepo;
  private final IdempotencyService idempotency;
  private final WrongItemProducer producer;
  private final SnowflakeIdGenerator ids;
  private final ObjectMapper objectMapper;

  public WrongItemService(
      WrongItemRepository itemRepo,
      WrongItemTagRepository tagRepo,
      WrongItemImageRepository imageRepo,
      TagTaxonomyRepository taxonomyRepo,
      AuditLogRepository auditRepo,
      IdempotencyService idempotency,
      WrongItemProducer producer,
      SnowflakeIdGenerator ids,
      ObjectMapper objectMapper) {
    this.itemRepo = itemRepo;
    this.tagRepo = tagRepo;
    this.imageRepo = imageRepo;
    this.taxonomyRepo = taxonomyRepo;
    this.auditRepo = auditRepo;
    this.idempotency = idempotency;
    this.producer = producer;
    this.ids = ids;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public WrongItemVO create(CreateWrongItemReq req, String requestId) {
    Optional<Long> cached = idempotency.peek(requestId);
    if (cached.isPresent()) {
      return loadVo(cached.get())
          .orElseThrow(() -> new IllegalStateException("cached id missing: " + cached.get()));
    }
    WrongItem e = new WrongItem();
    e.setId(ids.nextId());
    e.setStudentId(req.studentId());
    e.setSubject(req.subject());
    e.setGradeCode(req.gradeCode());
    e.setSourceType(req.sourceType());
    e.setOriginImageKey(req.originImageKey());
    e.setProcessedImageKey(req.processedImageKey());
    e.setOcrText(req.ocrText());
    e.setStemText(req.stemText());
    e.setStatus(WrongItemStatus.DRAFT.code());
    e.setMastery((short) 0);
    e.setDifficulty(req.difficulty());
    WrongItem saved = itemRepo.save(e);

    writeAudit(saved.getId(), "create", req, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            saved.getId(),
            WrongItemChangedEvent.ACTION_CREATED,
            saved.getVersion(),
            Instant.now()));

    idempotency.tryClaim(requestId, saved.getId());
    return toVo(saved);
  }

  @Transactional(readOnly = true)
  public Optional<WrongItemVO> findById(Long id) {
    return loadVo(id);
  }

  @Transactional(readOnly = true)
  public WrongItemPageVO page(
      String subject,
      String statusGroup,
      String tagCode,
      Long studentId,
      Long cursor,
      int size) {
    int capped = Math.min(Math.max(size, 1), 100);
    List<Short> statuses = resolveStatusGroup(statusGroup);
    List<WrongItem> rows = itemRepo.findPage(subject, statuses, tagCode, studentId, cursor, capped + 1);
    boolean more = rows.size() > capped;
    List<WrongItem> page = more ? rows.subList(0, capped) : rows;
    String nextCursor = more ? String.valueOf(page.get(page.size() - 1).getId()) : null;
    return new WrongItemPageVO(page.stream().map(this::toVo).toList(), nextCursor);
  }

  @Transactional
  public WrongItemVO update(Long id, UpdateWrongItemReq req, String requestId) {
    WrongItem e =
        itemRepo
            .findById(id)
            .orElseThrow(() -> new NotFoundException("wrong_item not found: " + id));
    if (!e.getVersion().equals(req.version())) {
      throw new ObjectOptimisticLockingFailureException(WrongItem.class, id);
    }
    if (req.stemText() != null) {
      e.setStemText(req.stemText());
    }
    if (req.ocrText() != null) {
      e.setOcrText(req.ocrText());
    }
    if (req.status() != null) {
      WrongItemStatus current = WrongItemStatus.fromCode(e.getStatus());
      WrongItemStatus target = WrongItemStatus.fromCode(req.status());
      if (!current.allowedNext().contains(target)) {
        throw new IllegalStateException(
            "status transition not allowed: " + current + " -> " + target);
      }
      e.setStatus(req.status());
      if (target == WrongItemStatus.MASTERED) {
        e.setMasteredAt(Instant.now());
      }
    }
    if (req.mastery() != null) {
      e.setMastery(req.mastery());
    }
    WrongItem saved = itemRepo.save(e);
    writeAudit(saved.getId(), "update", req, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            saved.getId(),
            WrongItemChangedEvent.ACTION_UPDATED,
            saved.getVersion(),
            Instant.now()));
    return toVo(saved);
  }

  @Transactional
  public void delete(Long id, String requestId) {
    WrongItem e =
        itemRepo
            .findById(id)
            .orElseThrow(() -> new NotFoundException("wrong_item not found: " + id));
    Long version = e.getVersion();
    itemRepo.delete(e);
    writeAudit(id, "delete", null, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            id, WrongItemChangedEvent.ACTION_DELETED, version, Instant.now()));
  }

  /** G-01: bulk replace tags (PATCH semantics — delete-all then re-insert). */
  @Transactional
  public void replaceTags(Long itemId, BulkTagReq req, Long ifMatchVersion, String requestId) {
    WrongItem item =
        itemRepo
            .findById(itemId)
            .orElseThrow(() -> new NotFoundException("wrong_item not found: " + itemId));
    if (!item.getVersion().equals(ifMatchVersion)) {
      throw new ObjectOptimisticLockingFailureException(WrongItem.class, itemId);
    }
    tagRepo.deleteByWrongItemId(itemId);
    if (req.tags() != null) {
      for (String code : req.tags()) {
        TagTaxonomy tag =
            taxonomyRepo
                .findByCode(code)
                .orElseThrow(() -> new NotFoundException("tag not found: " + code));
        WrongItemTag row = new WrongItemTag();
        row.setId(ids.nextId());
        row.setWrongItemId(itemId);
        row.setTagCode(tag.getCode());
        row.setWeight(new BigDecimal("1.000"));
        tagRepo.save(row);
      }
    }
    writeAudit(itemId, "replace-tags", req, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            itemId,
            WrongItemChangedEvent.ACTION_UPDATED,
            item.getVersion(),
            Instant.now()));
  }

  /** G-02: return active tags from taxonomy. */
  @Transactional(readOnly = true)
  public List<TagTaxonomy> getActiveTags(String subject) {
    if (subject != null && !subject.isBlank()) {
      return taxonomyRepo.findBySubjectAndStatus(subject, (short) 1);
    }
    return taxonomyRepo.findByStatus((short) 1);
  }

  @Transactional
  public WrongItemImageVO confirmImage(Long itemId, ConfirmImageReq req, String requestId) {
    WrongItem item =
        itemRepo
            .findById(itemId)
            .orElseThrow(() -> new NotFoundException("wrong_item not found: " + itemId));
    if ("ORIGIN".equals(req.role())) {
      imageRepo
          .findByWrongItemIdAndRole(itemId, "ORIGIN")
          .ifPresent(existing -> imageRepo.delete(existing));
    }
    WrongItemImage img = new WrongItemImage();
    img.setId(ids.nextId());
    img.setWrongItemId(itemId);
    img.setObjectKey(req.objectKey());
    img.setRole(req.role());
    img.setWidthPx(req.widthPx());
    img.setHeightPx(req.heightPx());
    img.setByteSize(req.byteSize());
    img.setContentType(req.contentType());
    WrongItemImage saved = imageRepo.save(img);
    if ("ORIGIN".equals(req.role())) {
      item.setOriginImageKey(req.objectKey());
    } else if ("PROCESSED".equals(req.role())) {
      item.setProcessedImageKey(req.objectKey());
    }
    itemRepo.save(item);
    writeAudit(itemId, "confirm-image", req, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            itemId,
            WrongItemChangedEvent.ACTION_UPDATED,
            item.getVersion(),
            Instant.now()));
    return new WrongItemImageVO(
        saved.getObjectKey(),
        saved.getRole(),
        saved.getWidthPx(),
        saved.getHeightPx(),
        saved.getByteSize());
  }

  @Transactional
  public WrongItemVO setDifficulty(Long itemId, SetDifficultyReq req, String requestId) {
    WrongItem item =
        itemRepo
            .findById(itemId)
            .orElseThrow(() -> new NotFoundException("wrong_item not found: " + itemId));
    item.setDifficulty(req.level());
    WrongItem saved = itemRepo.save(item);
    writeAudit(itemId, "set-difficulty", req, requestId);
    producer.publish(
        new WrongItemChangedEvent(
            itemId,
            WrongItemChangedEvent.ACTION_UPDATED,
            saved.getVersion(),
            Instant.now()));
    return toVo(saved);
  }

  private Optional<WrongItemVO> loadVo(Long id) {
    return itemRepo.findById(id).map(this::toVo);
  }

  private WrongItemVO toVo(WrongItem e) {
    List<WrongItemTagVO> tags =
        tagRepo.findByWrongItemIdOrderByIdAsc(e.getId()).stream()
            .map(t -> new WrongItemTagVO(t.getTagCode(), t.getWeight()))
            .toList();
    List<WrongItemImageVO> images =
        imageRepo.findByWrongItemIdOrderByIdAsc(e.getId()).stream()
            .map(
                i ->
                    new WrongItemImageVO(
                        i.getObjectKey(),
                        i.getRole(),
                        i.getWidthPx(),
                        i.getHeightPx(),
                        i.getByteSize()))
            .toList();
    return new WrongItemVO(
        e.getId(),
        e.getStudentId(),
        e.getSubject(),
        e.getGradeCode(),
        e.getSourceType(),
        e.getOriginImageKey(),
        e.getProcessedImageKey(),
        e.getOcrText(),
        e.getStemText(),
        mapStatus(e.getStatus()),
        e.getMastery(),
        e.getDifficulty(),
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        tags,
        images);
  }

  /** G-03: maps internal SMALLINT status to frontend string. */
  private static String mapStatus(Short code) {
    if (code == null) return "pending";
    return switch (code) {
      case 0 -> "pending";
      case 1 -> "analyzing";
      default -> "completed";
    };
  }

  /** "active" = all non-mastered/non-archived; "mastered" = mastered only. */
  private static List<Short> resolveStatusGroup(String statusGroup) {
    if (statusGroup == null) return null;
    return switch (statusGroup) {
      case "active" -> List.of((short) 0, (short) 1, (short) 2, (short) 3);
      case "mastered" -> List.of((short) 8);
      default -> null;
    };
  }

  private void writeAudit(Long targetId, String action, Object payload, String requestId) {
    AuditLog row = new AuditLog();
    row.setId(ids.nextId());
    row.setActorType("USER");
    row.setAction(action);
    row.setTargetType("wrong_item");
    row.setTargetId(targetId);
    row.setRequestId(requestId);
    if (payload != null) {
      try {
        row.setPayload(objectMapper.writeValueAsString(payload));
      } catch (JsonProcessingException ignored) {
        row.setPayload("{}");
      }
    }
    auditRepo.save(row);
  }

  /** Domain-level 404. GlobalExceptionHandler maps this to HTTP 404. */
  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String msg) {
      super(msg);
    }
  }
}
