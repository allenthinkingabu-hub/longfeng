package com.longfeng.fileservice.scan;

import java.io.InputStream;

/** 病毒扫描 SPI · 本 Phase ClamStub 返 clean · S10 对接 ClamAV. */
public interface AntivirusClient {

  /** 扫描结果. */
  enum Verdict {
    CLEAN,
    INFECTED,
    ERROR
  }

  ScanResult scan(InputStream content);

  /** record · 传递扫描结果 + 可选 reason（INFECTED/ERROR 时填）. */
  record ScanResult(Verdict verdict, String reason) {
    public static ScanResult clean() {
      return new ScanResult(Verdict.CLEAN, null);
    }
  }
}
