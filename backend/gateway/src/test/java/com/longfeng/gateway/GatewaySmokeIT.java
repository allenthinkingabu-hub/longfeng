package com.longfeng.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Gateway smoke: /actuator/health = UP, no token required · 落地计划 §6.7 Step 13. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySmokeIT {

  @LocalServerPort private int port;
  @Autowired private org.springframework.context.ApplicationContext ctx;

  @Test
  void healthIsUp() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }
}
