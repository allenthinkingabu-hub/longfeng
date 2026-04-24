package com.longfeng.fileservice.scan;

import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * ClamStub · 本 Phase 默认实现 · 所有输入返 CLEAN · 留 S10 对接 ClamAV HTTP/gRPC 客户端.
 *
 * <p>IT 可 @TestConfiguration 覆盖返 INFECTED 测错误路径.
 */
@Component
public class ClamStub implements AntivirusClient {

  @Override
  public ScanResult scan(InputStream content) {
    return ScanResult.clean();
  }
}
