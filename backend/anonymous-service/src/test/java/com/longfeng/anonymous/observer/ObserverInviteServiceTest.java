package com.longfeng.anonymous.observer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.anonymous.entity.ObserverInvite;
import com.longfeng.anonymous.entity.ObserverSession;
import com.longfeng.anonymous.support.SnowflakeIdGenerator;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObserverInviteService}.
 *
 * <p>Key scenarios:
 * <ul>
 *   <li>Generate invite — happy path, role validation.
 *   <li>Exchange PARENT invite → 30-day session TTL (D-Observer-TTL).
 *   <li>Exchange TEACHER invite → 90-day session TTL (D-Observer-TTL).
 *   <li>Exchange — invite not found / expired.
 *   <li>Exchange — CAS conflict (already used).
 *   <li>C4: issued JWT always contains scope=READ.
 * </ul>
 *
 * <p>Uses inline stubs for repositories to avoid Mockito generic JPA issues (F-02).
 */
class ObserverInviteServiceTest {

  private static final String TEST_ANON_SECRET = "test-anon-secret-at-least-32-bytes!!";

  private StubObserverInviteRepository inviteRepo;
  private StubObserverSessionRepository sessionRepo;
  private SnowflakeIdGenerator idGen;
  private ObserverInviteService service;

  @BeforeEach
  void setUp() {
    inviteRepo = new StubObserverInviteRepository();
    sessionRepo = new StubObserverSessionRepository();
    idGen = new SnowflakeIdGenerator(5);
    service = new ObserverInviteService(inviteRepo, sessionRepo, idGen);
    injectSecret(service, TEST_ANON_SECRET);
  }

  // ── Generate invite ───────────────────────────────────────────────────────

  @Test
  void generateInvite_parentRole_happyPath() {
    ObserverInvite invite = service.generateInvite(100L, ObserverInvite.ROLE_PARENT);

    assertThat(invite).isNotNull();
    assertThat(invite.getStudentId()).isEqualTo(100L);
    assertThat(invite.getRole()).isEqualTo(ObserverInvite.ROLE_PARENT);
    assertThat(invite.getStatus()).isEqualTo(ObserverInvite.STATUS_PENDING);
    assertThat(invite.getInviteCode()).hasSize(6);
    // Expires ~24h from now
    assertThat(invite.getExpiresAt())
        .isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23))
        .isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusHours(25));
  }

  @Test
  void generateInvite_teacherRole_happyPath() {
    ObserverInvite invite = service.generateInvite(200L, ObserverInvite.ROLE_TEACHER);
    assertThat(invite.getRole()).isEqualTo(ObserverInvite.ROLE_TEACHER);
  }

  @Test
  void generateInvite_invalidRole_throws() {
    assertThatThrownBy(() -> service.generateInvite(100L, "ADMIN"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).errCode())
            .isEqualTo(ErrCode.VALIDATION_FAILED));
  }

  @Test
  void generateInvite_codeIsAlphanumericUppercase() {
    ObserverInvite invite = service.generateInvite(100L, ObserverInvite.ROLE_PARENT);
    assertThat(invite.getInviteCode()).matches("[A-Z2-9]+");
  }

  // ── Exchange — D-Observer-TTL: PARENT 30d ────────────────────────────────

  @Test
  void exchange_parentInvite_creates30DaySession() throws Exception {
    ObserverInvite invite = givenPendingInvite("ABC123", 55L, ObserverInvite.ROLE_PARENT);

    String rawJwt = service.exchange("ABC123", "device-fp");

    assertThat(rawJwt).isNotBlank();
    assertThat(sessionRepo.saved).hasSize(1);
    ObserverSession session = sessionRepo.saved.get(0);
    assertThat(session.getStudentId()).isEqualTo(55L);
    assertThat(session.getRole()).isEqualTo(ObserverInvite.ROLE_PARENT);

    // TTL: 30d from now (±1 min tolerance)
    OffsetDateTime expectedExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
    assertThat(session.getExpiresAt())
        .isAfter(expectedExpiry.minusMinutes(1))
        .isBefore(expectedExpiry.plusMinutes(1));

    // C4: JWT must carry scope=READ
    SignedJWT jwt = SignedJWT.parse(rawJwt);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getStringClaim("scope")).isEqualTo(ObserverSession.SCOPE_READ);
  }

  // ── Exchange — D-Observer-TTL: TEACHER 90d ───────────────────────────────

  @Test
  void exchange_teacherInvite_creates90DaySession() throws Exception {
    givenPendingInvite("DEF456", 77L, ObserverInvite.ROLE_TEACHER);

    String rawJwt = service.exchange("DEF456", null);

    assertThat(sessionRepo.saved).hasSize(1);
    ObserverSession session = sessionRepo.saved.get(0);
    assertThat(session.getRole()).isEqualTo(ObserverInvite.ROLE_TEACHER);

    // TTL: 90d from now (±1 min tolerance)
    OffsetDateTime expectedExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(90);
    assertThat(session.getExpiresAt())
        .isAfter(expectedExpiry.minusMinutes(1))
        .isBefore(expectedExpiry.plusMinutes(1));

    // C4: JWT scope must be READ
    SignedJWT jwt = SignedJWT.parse(rawJwt);
    assertThat(jwt.getJWTClaimsSet().getStringClaim("scope"))
        .isEqualTo(ObserverSession.SCOPE_READ);
  }

  // ── Exchange — not found ──────────────────────────────────────────────────

  @Test
  void exchange_unknownCode_throwsTokenExpired() {
    assertThatThrownBy(() -> service.exchange("XXXXXX", null))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).errCode())
            .isEqualTo(ErrCode.TOKEN_EXPIRED));
  }

  // ── Exchange — CAS already used ───────────────────────────────────────────

  @Test
  void exchange_casAlreadyUsed_throwsTokenExpired() {
    givenPendingInvite("GHI789", 10L, ObserverInvite.ROLE_PARENT);
    inviteRepo.casReturnZero = true; // simulate concurrent exchange

    assertThatThrownBy(() -> service.exchange("GHI789", null))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).errCode())
            .isEqualTo(ErrCode.TOKEN_EXPIRED));
  }

  // ── C4: scope=READ enforcement ───────────────────────────────────────────

  @Test
  void exchange_issuedJwtNeverHasScopeWrite() throws Exception {
    givenPendingInvite("JKL012", 30L, ObserverInvite.ROLE_PARENT);
    String rawJwt = service.exchange("JKL012", null);
    SignedJWT jwt = SignedJWT.parse(rawJwt);
    String scope = jwt.getJWTClaimsSet().getStringClaim("scope");
    assertThat(scope).isEqualTo("READ");
    assertThat(scope).isNotEqualTo("WRITE");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private ObserverInvite givenPendingInvite(String code, Long studentId, String role) {
    ObserverInvite invite = new ObserverInvite();
    invite.setId(idGen.nextId());
    invite.setInviteCode(code);
    invite.setStudentId(studentId);
    invite.setRole(role);
    invite.setStatus(ObserverInvite.STATUS_PENDING);
    invite.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    inviteRepo.pendingByCode.put(code, invite);
    inviteRepo.byCode.put(code, invite);
    return invite;
  }

  private static void injectSecret(ObserverInviteService svc, String secret) {
    try {
      var field = ObserverInviteService.class.getDeclaredField("anonSecret");
      field.setAccessible(true);
      field.set(svc, secret);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // ── Inline Stub Repositories ──────────────────────────────────────────────

  static class StubObserverInviteRepository implements ObserverInviteRepository {
    final java.util.Map<String, ObserverInvite> pendingByCode = new java.util.HashMap<>();
    final java.util.Map<String, ObserverInvite> byCode = new java.util.HashMap<>();
    final List<ObserverInvite> saved = new ArrayList<>();
    boolean casReturnZero = false;

    @Override public <S extends ObserverInvite> S save(S entity) { saved.add(entity); return entity; }
    @Override public Optional<ObserverInvite> findPendingByCode(String code) {
      return Optional.ofNullable(pendingByCode.get(code));
    }
    @Override public Optional<ObserverInvite> findByInviteCode(String code) {
      return Optional.ofNullable(byCode.get(code));
    }
    @Override public List<ObserverInvite> findByStudentIdOrdered(Long studentId, int limit) { return List.of(); }
    @Override public int casMarkExchanged(Long id) {
      if (casReturnZero) return 0;
      // Mark the invite as exchanged
      pendingByCode.values().stream()
          .filter(i -> id.equals(i.getId()))
          .findFirst()
          .ifPresent(i -> {
            i.setStatus(ObserverInvite.STATUS_EXCHANGED);
            pendingByCode.values().remove(i);
          });
      return 1;
    }
    @Override public int expireBefore(OffsetDateTime now) { return 0; }
    @Override public Optional<ObserverInvite> findById(Long id) { return Optional.empty(); }
    @Override public List<ObserverInvite> findAll() { return saved; }
    @Override public <S extends ObserverInvite> List<S> saveAll(Iterable<S> entities) { return List.of(); }
    @Override public boolean existsById(Long id) { return false; }
    @Override public long count() { return saved.size(); }
    @Override public void deleteById(Long id) {}
    @Override public void delete(ObserverInvite entity) {}
    @Override public void deleteAll() {}
    @Override public void deleteAll(Iterable<? extends ObserverInvite> entities) {}
    @Override public void deleteAllById(Iterable<? extends Long> ids) {}
    @Override public List<ObserverInvite> findAllById(Iterable<Long> ids) { return List.of(); }
    @Override public void flush() {}
    @Override public <S extends ObserverInvite> S saveAndFlush(S entity) { return save(entity); }
    @Override public <S extends ObserverInvite> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
    @Override public void deleteAllInBatch(Iterable<ObserverInvite> entities) {}
    @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
    @Override public void deleteAllInBatch() {}
    @Override public ObserverInvite getOne(Long id) { throw new UnsupportedOperationException(); }
    @Override public ObserverInvite getById(Long id) { throw new UnsupportedOperationException(); }
    @Override public ObserverInvite getReferenceById(Long id) { throw new UnsupportedOperationException(); }
    @Override public <S extends ObserverInvite> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
    @Override public <S extends ObserverInvite> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
    @Override public <S extends ObserverInvite> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
    @Override public <S extends ObserverInvite> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    @Override public <S extends ObserverInvite> long count(org.springframework.data.domain.Example<S> example) { return 0; }
    @Override public <S extends ObserverInvite> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
    @Override public <S extends ObserverInvite, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    @Override public org.springframework.data.domain.Page<ObserverInvite> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    @Override public List<ObserverInvite> findAll(org.springframework.data.domain.Sort sort) { return saved; }
  }

  static class StubObserverSessionRepository implements ObserverSessionRepository {
    final List<ObserverSession> saved = new ArrayList<>();

    @Override public <S extends ObserverSession> S save(S entity) { saved.add(entity); return entity; }
    @Override public Optional<ObserverSession> findById(Long id) { return Optional.empty(); }
    @Override public Optional<ObserverSession> findByJti(String jti) { return Optional.empty(); }
    @Override public List<ObserverSession> findActiveByStudentId(Long studentId) { return List.of(); }
    @Override public int casRevoke(String jti, int expectedVersion, OffsetDateTime revokedAt) { return 0; }
    @Override public int expireBefore(OffsetDateTime now) { return 0; }
    @Override public List<ObserverSession> findExpiringBatch(OffsetDateTime now, int limit) { return List.of(); }
    @Override public List<ObserverSession> findAll() { return saved; }
    @Override public <S extends ObserverSession> List<S> saveAll(Iterable<S> entities) { return List.of(); }
    @Override public boolean existsById(Long id) { return false; }
    @Override public long count() { return saved.size(); }
    @Override public void deleteById(Long id) {}
    @Override public void delete(ObserverSession entity) {}
    @Override public void deleteAll() {}
    @Override public void deleteAll(Iterable<? extends ObserverSession> entities) {}
    @Override public void deleteAllById(Iterable<? extends Long> ids) {}
    @Override public List<ObserverSession> findAllById(Iterable<Long> ids) { return List.of(); }
    @Override public void flush() {}
    @Override public <S extends ObserverSession> S saveAndFlush(S entity) { return save(entity); }
    @Override public <S extends ObserverSession> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
    @Override public void deleteAllInBatch(Iterable<ObserverSession> entities) {}
    @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
    @Override public void deleteAllInBatch() {}
    @Override public ObserverSession getOne(Long id) { throw new UnsupportedOperationException(); }
    @Override public ObserverSession getById(Long id) { throw new UnsupportedOperationException(); }
    @Override public ObserverSession getReferenceById(Long id) { throw new UnsupportedOperationException(); }
    @Override public <S extends ObserverSession> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
    @Override public <S extends ObserverSession> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
    @Override public <S extends ObserverSession> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
    @Override public <S extends ObserverSession> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    @Override public <S extends ObserverSession> long count(org.springframework.data.domain.Example<S> example) { return 0; }
    @Override public <S extends ObserverSession> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
    @Override public <S extends ObserverSession, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    @Override public org.springframework.data.domain.Page<ObserverSession> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    @Override public List<ObserverSession> findAll(org.springframework.data.domain.Sort sort) { return saved; }
  }
}
