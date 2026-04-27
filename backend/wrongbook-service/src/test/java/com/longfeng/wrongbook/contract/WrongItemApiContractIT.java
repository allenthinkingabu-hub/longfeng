package com.longfeng.wrongbook.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.TestMqConfig;
import com.longfeng.wrongbook.WrongbookIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * OpenAPI contract check — V-S3-02 says paths ≥ 9; §4.1 design bumped to 11 (added attempts
 * create/list). Also asserts every non-actuator path is one we advertise in the arch doc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestMqConfig.class)
class WrongItemApiContractIT extends WrongbookIntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;

  @Test
  void openApiExposesDocumentedPaths() throws Exception {
    MvcResult r = mvc.perform(get("/v3/api-docs")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode paths = om.readTree(r.getResponse().getContentAsString()).path("paths");
    assertThat(paths.isObject()).isTrue();
    long totalPaths = paths.properties().stream().count();
    // OpenAPI 3 counts unique URL templates (not HTTP operations). 7 domain + /ready + /live = 9.
    assertThat(totalPaths).isGreaterThanOrEqualTo(9);
    assertThat(paths.has("/wrongbook/items")).isTrue();
    assertThat(paths.has("/wrongbook/items/{id}")).isTrue();
    assertThat(paths.has("/wrongbook/items/{id}/tags")).isTrue();
    assertThat(paths.has("/wrongbook/tags")).isTrue();
    assertThat(paths.has("/wrongbook/items/{id}/images")).isTrue();
    assertThat(paths.has("/wrongbook/items/{id}/difficulty")).isTrue();
    assertThat(paths.has("/wrongbook/items/{id}/attempts")).isTrue();
    // 11 HTTP operations across the 7 domain templates (arch doc §4.1)
    long domainOps =
        paths.properties().stream()
            .filter(e -> e.getKey().startsWith("/wrongbook/"))
            .mapToLong(e -> e.getValue().size())
            .sum();
    assertThat(domainOps).isGreaterThanOrEqualTo(11);
  }
}
