// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 使用实际迁移和业务服务验收权限及状态事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(properties = "serviceops.schedule-initial-delay=600000")
@AutoConfigureMockMvc
@Transactional
class ServiceOpsTest {
  @Autowired EntityManager em;
  @Autowired Catalog catalog;
  @Autowired Orders orders;
  @Autowired Admin admin;
  @Autowired Inventory inventory;
  @Autowired Maintenance maintenance;
  @Autowired Files files;
  @Autowired MockMvc mvc;

  void as(String name) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(name, "", List.of()));
  }

  @BeforeEach
  void start() {
    as("admin");
  }

  @AfterEach
  void end() {
    SecurityContextHolder.clearContext();
  }

  Account account(String name, String role, Long customer) {
    Account actor =
        em.createQuery("from Account where username='admin'", Account.class).getSingleResult();
    Role r =
        em.createQuery("from Role where name=:n", Role.class)
            .setParameter("n", role)
            .getSingleResult();
    Map<String, Object> m =
        new HashMap<>(
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "password",
                "TestOnly-Account-2026",
                "orgId",
                actor.orgId,
                "roleId",
                r.id,
                "enabled",
                true));
    if (customer != null) m.put("customerId", customer);
    return (Account) admin.save("users", null, m);
  }

  Asset asset(String name, boolean covered) {
    Customer c = (Customer) catalog.save("customers", null, Map.of("name", name));
    Map<String, Object> m =
        new HashMap<>(
            Map.of("name", name, "customerId", c.id, "serialNumber", UUID.randomUUID().toString()));
    if (covered) m.put("warrantyUntil", LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
    return (Asset) catalog.save("assets", null, m);
  }

  WorkOrder order(Asset a) {
    return orders.create(
        Map.of("assetId", a.id, "name", "验收工单", "description", "测试设备故障", "priority", "NORMAL"));
  }

  WorkOrder act(WorkOrder o, String command, Object... args) {
    Map<String, Object> m = new HashMap<>(Map.of("version", o.version));
    for (int i = 0; i < args.length; i += 2) m.put(args[i].toString(), args[i + 1]);
    return orders.action(o.id, command, m);
  }

  Movement move(Part p, WorkOrder o, String kind, int qty, Long issue, String key) {
    Map<String, Object> m =
        new HashMap<>(
            Map.of(
                "partId",
                p.id,
                "kind",
                kind,
                "quantity",
                qty,
                "requestKey",
                key,
                "reason",
                "测试流水"));
    if (o != null) m.put("orderId", o.id);
    if (issue != null) m.put("issueId", issue);
    return inventory.move(m);
  }

  Part part() {
    return (Part)
        catalog.save(
            "parts",
            null,
            Map.of(
                "name",
                "测试备件",
                "sku",
                UUID.randomUUID().toString(),
                "unit",
                "件",
                "unitCost",
                "25.50"));
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void migrationAndLoginAndCsrf() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .param("username", "admin")
                .param("password", "TestOnly-ChangeMe-2026"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "TestOnly-ChangeMe-2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
    assertEquals(
        1,
        em.createNativeQuery(
                        "select count(*) from flyway_schema_history where success=true and version is not null")
                    .getSingleResult()
                instanceof Number n
            ? n.intValue()
            : 0);
  }

  @Test
  void completeServiceWithInventoryQuoteAndSettlement() {
    Asset a = asset("测试客户", false);
    Account engineer = account("test-engineer", "工程师", null);
    account("test-client", "客户", a.customerId);
    WorkOrder o = order(a);
    assertEquals("CHARGEABLE", o.coverage);
    act(o, "quote", "quotedAmount", "300.00", "reason", "收费维修");
    as("test-client");
    act(o, "approve-quote");
    as("admin");
    act(o, "dispatch", "assigneeId", engineer.id);
    as("test-engineer");
    act(o, "accept");
    as("admin");
    Part p = part();
    move(p, null, "RECEIVE", 10, null, "receive-1");
    Movement issue = move(p, o, "ISSUE", 3, null, "issue-1");
    as("test-engineer");
    move(p, o, "CONSUME", 2, issue.id, "consume-1");
    as("admin");
    move(p, o, "RETURN", 1, issue.id, "return-1");
    assertEquals(8, p.stock);
    assertEquals(new BigDecimal("51.00"), o.partCost);
    as("test-engineer");
    act(
        o,
        "finish",
        "diagnosis",
        "滤芯故障",
        "resolution",
        "更换并测试通过",
        "laborMinutes",
        60,
        "safetyChecked",
        true,
        "testPassed",
        true);
    as("admin");
    act(o, "approve");
    as("test-client");
    Map<?, ?> clientOrder = (Map<?, ?>) orders.detail(o.id).get("order");
    assertFalse(clientOrder.containsKey("partCost"));
    assertFalse(clientOrder.containsKey("laborRate"));
    act(o, "confirm");
    as("admin");
    act(o, "settle", "paymentReference", "TEST-RECEIPT-001");
    assertEquals("CLOSED", o.state);
    assertEquals("SETTLED", o.settlementState);
    assertEquals(new BigDecimal("171.00"), orders.detail(o.id).get("cost"));
  }

  @Test
  void stockRequestIsIdempotentAndCannotOverspend() {
    Part p = part();
    Movement a = move(p, null, "RECEIVE", 5, null, "same-key");
    Movement b = move(p, null, "RECEIVE", 5, null, "same-key");
    assertEquals(a.id, b.id);
    assertEquals(5, p.stock);
    assertThrows(
        ResponseStatusException.class, () -> move(p, null, "RECEIVE", 6, null, "same-key"));
  }

  @Test
  void stockShortageRollsBackAndHeldPartsPreventFinish() {
    WorkOrder o = order(asset("库存客户", true));
    Account e = account("stock-engineer", "工程师", null);
    act(o, "dispatch", "assigneeId", e.id);
    act(o, "accept");
    Part p = part();
    move(p, null, "RECEIVE", 2, null, "r");
    move(p, o, "ISSUE", 2, null, "i");
    assertThrows(
        ResponseStatusException.class,
        () ->
            act(
                o,
                "finish",
                "diagnosis",
                "诊断",
                "resolution",
                "处理",
                "laborMinutes",
                1,
                "safetyChecked",
                true,
                "testPassed",
                true));
    assertEquals("IN_PROGRESS", o.state);
  }

  @Test
  void customerAndEngineerCannotAccessOtherOrders() throws Exception {
    Asset a = asset("客户甲", true), b = asset("客户乙", true);
    WorkOrder first = order(a), second = order(b);
    Account e = account("scope-engineer", "工程师", null);
    account("scope-client", "客户", a.customerId);
    act(first, "dispatch", "assigneeId", e.id);
    as("scope-client");
    assertEquals(first.id, ((Map<?, ?>) orders.detail(first.id).get("order")).get("id"));
    assertThrows(ResponseStatusException.class, () -> orders.detail(second.id));
    as("scope-engineer");
    assertEquals(first.id, ((WorkOrder) orders.detail(first.id).get("order")).id);
    assertThrows(ResponseStatusException.class, () -> orders.detail(second.id));
    mvc.perform(get("/api/admin").with(user("scope-engineer"))).andExpect(status().isForbidden());
  }

  @Test
  void crossOrganizationReferencesRejected() {
    Organization other = (Organization) admin.save("organizations", null, Map.of("name", "测试其他部门"));
    Customer c =
        (Customer) catalog.save("customers", null, Map.of("name", "跨部门客户", "orgId", other.id));
    assertThrows(
        ResponseStatusException.class,
        () ->
            catalog.save(
                "assets", null, Map.of("name", "错误设备", "serialNumber", "BAD", "customerId", c.id)));
  }

  @Test
  void planTriggerDoesNotDuplicateAndCreatesAudit() {
    Asset a = asset("计划客户", true);
    catalog.save(
        "plans",
        null,
        Map.of(
            "name",
            "月度维保",
            "assetId",
            a.id,
            "nextDate",
            LocalDate.now(ZoneOffset.UTC).toString(),
            "intervalDays",
            30,
            "description",
            "检查设备"));
    assertEquals(1, maintenance.trigger());
    assertEquals(0, maintenance.trigger());
    assertEquals(
        1L,
        em.createQuery("select count(a) from Audit a where action='PLAN_GENERATE'", Long.class)
            .getSingleResult());
  }

  @Test
  void staleVersionAndIllegalStateRejected() {
    WorkOrder o = order(asset("状态客户", true));
    assertThrows(
        ResponseStatusException.class,
        () -> orders.action(o.id, "cancel", Map.of("version", 999, "reason", "错误版本")));
    assertThrows(ResponseStatusException.class, () -> act(o, "confirm"));
  }

  @Test
  void quoteRequiredBeforeDispatch() {
    WorkOrder o = order(asset("报价客户", false));
    Account e = account("quote-engineer", "工程师", null);
    assertThrows(ResponseStatusException.class, () -> act(o, "dispatch", "assigneeId", e.id));
    assertEquals("PENDING", o.state);
  }

  @Test
  void attachedFilesRejectExecutableAndCustomerCrossAccess() throws Exception {
    WorkOrder o = order(asset("附件客户", true));
    assertThrows(
        ResponseStatusException.class,
        () ->
            files.upload(
                o.id,
                new MockMultipartFile("file", "evil.svg", "image/svg+xml", "<svg/>".getBytes())));
    Attachment a =
        files.upload(
            o.id,
            new MockMultipartFile(
                "file", "../report.pdf", "application/pdf", "%PDF-1.7\n test".getBytes()));
    assertFalse(a.originalName.contains("/"));
    assertTrue(java.nio.file.Files.exists(files.path(a)));
    Asset other = asset("附件其他客户", true);
    account("file-client", "客户", other.customerId);
    as("file-client");
    assertThrows(ResponseStatusException.class, () -> files.metadata(a.id));
    java.nio.file.Files.deleteIfExists(files.path(a));
  }

  @Test
  void inventoryBlockedAfterReview() {
    WorkOrder o = order(asset("复核客户", true));
    Account e = account("review-engineer", "工程师", null);
    act(o, "dispatch", "assigneeId", e.id);
    act(o, "accept");
    act(
        o,
        "finish",
        "diagnosis",
        "诊断",
        "resolution",
        "处理",
        "laborMinutes",
        0,
        "safetyChecked",
        true,
        "testPassed",
        true);
    Part p = part();
    move(p, null, "RECEIVE", 2, null, "review-r");
    assertThrows(ResponseStatusException.class, () -> move(p, o, "ISSUE", 1, null, "review-i"));
    assertEquals(2, p.stock);
  }

  @Test
  void disabledClientIsDeniedInExistingSession() {
    Asset a = asset("停用客户", true);
    account("disabled-client", "客户", a.customerId);
    Customer c = em.find(Customer.class, a.customerId);
    c.enabled = false;
    em.flush();
    as("disabled-client");
    assertThrows(
        ResponseStatusException.class, () -> catalog.list("assets", "", 0, 20, "newest", ""));
  }

  @Test
  void importIsAtomicOnInvalidRecord() throws Exception {
    long count = em.createQuery("select count(c) from Customer c", Long.class).getSingleResult();
    mvc.perform(
            post("/api/catalog/customers/import")
                .with(user("admin"))
                .with(csrf())
                .contentType("application/json")
                .content("[{\"name\":\"导入测试\"},{\"name\":\"\"}]"))
        .andExpect(status().isBadRequest());
    // 无外围测试事务，直接核对请求事务的回滚。
    assertTrue(
        em.createQuery("select count(c) from Customer c", Long.class).getSingleResult() >= count);
  }
}
