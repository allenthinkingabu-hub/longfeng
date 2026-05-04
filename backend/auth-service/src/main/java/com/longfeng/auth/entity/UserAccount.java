package com.longfeng.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Maps lfwb.user_account table · auth-service read/upsert for WeChat login · S7 BUG-LF-09. */
@Entity
@Table(name = "user_account")
public class UserAccount {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "username", nullable = false, unique = true, length = 64)
  private String username;

  @Column(name = "phone_hash", length = 64)
  private String phoneHash;

  @Column(name = "email_hash", length = 64)
  private String emailHash;

  @Column(name = "role", nullable = false, length = 16)
  private String role;

  @Column(name = "grade_code", length = 16)
  private String gradeCode;

  @Column(name = "status", nullable = false)
  private Short status;

  @Column(name = "timezone", nullable = false, length = 32)
  private String timezone;

  @Column(name = "wechat_openid", length = 64, unique = true)
  private String wechatOpenid;

  @Column(name = "wechat_unionid", length = 64)
  private String wechatUnionid;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPhoneHash() {
    return phoneHash;
  }

  public void setPhoneHash(String phoneHash) {
    this.phoneHash = phoneHash;
  }

  public String getEmailHash() {
    return emailHash;
  }

  public void setEmailHash(String emailHash) {
    this.emailHash = emailHash;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getGradeCode() {
    return gradeCode;
  }

  public void setGradeCode(String gradeCode) {
    this.gradeCode = gradeCode;
  }

  public Short getStatus() {
    return status;
  }

  public void setStatus(Short status) {
    this.status = status;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getWechatOpenid() {
    return wechatOpenid;
  }

  public void setWechatOpenid(String wechatOpenid) {
    this.wechatOpenid = wechatOpenid;
  }

  public String getWechatUnionid() {
    return wechatUnionid;
  }

  public void setWechatUnionid(String wechatUnionid) {
    this.wechatUnionid = wechatUnionid;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
