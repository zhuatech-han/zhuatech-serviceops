// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 状态顺序、金额精度与CSV公式保护边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class WorkflowTest {
  @Test
  void cannotSkipReviewAndCustomerConfirmation() {
    assertThrows(ResponseStatusException.class, () -> Workflow.check("IN_PROGRESS", "confirm"));
    assertThrows(ResponseStatusException.class, () -> Workflow.check("REVIEW", "confirm"));
    assertDoesNotThrow(() -> Workflow.check("CONFIRM", "confirm"));
  }

  @Test
  void moneyAndCsvBoundaries() {
    assertThrows(
        ResponseStatusException.class,
        () -> Catalog.money(java.util.Map.of("amount", "1.001"), "amount"));
    assertThrows(
        ResponseStatusException.class,
        () -> Catalog.money(java.util.Map.of("amount", "-1"), "amount"));
    assertEquals("\"'=HYPERLINK(test)\"", Api.csv("=HYPERLINK(test)"));
    assertEquals("\"a\"\"b\"", Api.csv("a\"b"));
  }
}
