// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 同源业务接口；所有业务授权在服务层再次校验。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final Catalog catalog;
  final Orders orders;
  final Inventory inventory;
  final Maintenance maintenance;
  final Admin admin;
  final Access access;
  final Files files;
  final EntityManager em;
  final AdviceAdapter advice;

  public Api(
      Catalog c,
      Orders o,
      Inventory i,
      Maintenance m,
      Admin a,
      Access x,
      Files f,
      EntityManager e,
      AdviceAdapter v) {
    catalog = c;
    orders = o;
    inventory = i;
    maintenance = m;
    admin = a;
    access = x;
    files = f;
    em = e;
    advice = v;
  }

  /** 获取写请求校验令牌。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/auth/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  /** 读取当前账号及有效权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/auth/me")
  @Transactional(readOnly = true)
  Map<String, Object> me() {
    Account u = access.user();
    return Map.of("user", u, "permissions", new TreeSet<>(access.permissions(u)));
  }

  /** 验证旧密码并修改个人密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/auth/password")
  void password(@RequestBody Map<String, Object> m, HttpServletRequest request) {
    admin.password(m);
    request.getSession().invalidate();
  }

  /** 检索可见业务数据并分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog/{kind}")
  Map<String, Object> list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(defaultValue = "") String state) {
    return catalog.list(kind, q, page, size, sort, state);
  }

  /** 创建主数据资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/catalog/{kind}")
  Object create(@PathVariable String kind, @RequestBody Map<String, Object> m) {
    return catalog.save(kind, null, m);
  }

  /** 按版本更新主数据资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/catalog/{kind}/{id}")
  Object update(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> m) {
    return catalog.save(kind, id, m);
  }

  /** 停用主数据并保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/catalog/{kind}/{id}")
  void disable(@PathVariable String kind, @PathVariable long id, @RequestParam long version) {
    catalog.disable(kind, id, version);
  }

  /** 事务导入客户或备件资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/catalog/{kind}/import")
  @Transactional
  Object importData(@PathVariable String kind, @RequestBody List<Map<String, Object>> rows) {
    if (!Set.of("customers", "parts").contains(kind) || rows.isEmpty() || rows.size() > 100)
      throw Access.error(400, "仅支持1至100条客户或备件数据");
    List<Object> result = new ArrayList<>();
    for (var row : rows) result.add(catalog.save(kind, null, row));
    return Map.of("imported", result.size());
  }

  /** 读取组织角色与账号管理资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin")
  Object adminData() {
    return admin.data();
  }

  /** 创建管理资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  Object adminCreate(@PathVariable String kind, @RequestBody Map<String, Object> m) {
    return admin.save(kind, null, m);
  }

  /** 按版本更新管理资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  Object adminUpdate(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> m) {
    return admin.save(kind, id, m);
  }

  /** 登记设备服务请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders")
  Object orderCreate(@RequestBody Map<String, Object> m) {
    return Views.forUser(orders.create(m), access.user());
  }

  /** 读取授权范围内工单详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}")
  Object orderDetail(@PathVariable long id) {
    return orders.detail(id);
  }

  /** 执行合法的工单状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/{action}")
  Object action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> m) {
    return Views.forUser(orders.action(id, action, m), access.user());
  }

  /** 执行幂等的备件库存事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/inventory/movements")
  Object movement(@RequestBody Map<String, Object> m) {
    return inventory.move(m);
  }

  /** 触发到期维保计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/trigger")
  Object plans() {
    return Map.of("generated", maintenance.trigger());
  }

  /** 上传受限服务附件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/attachments")
  Object upload(@PathVariable long id, @RequestParam MultipartFile file)
      throws java.io.IOException {
    return files.upload(id, file);
  }

  /** 校验工单授权后下载附件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/attachments/{id}")
  ResponseEntity<?> download(@PathVariable long id) {
    Attachment a = files.metadata(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(a.originalName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new FileSystemResource(files.path(a)));
  }

  /** 返回可指派工程师资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lookups")
  @Transactional(readOnly = true)
  Map<String, Object> lookups() {
    Account u = access.user();
    List<Map<String, Object>> people = new ArrayList<>();
    if (u.customerId == null) {
      for (Account a :
          em.createQuery(
                  "from Account where enabled=true and "
                      + (access.admin(u) ? "1=1" : "orgId=" + u.orgId),
                  Account.class)
              .getResultList())
        if (a.customerId == null && access.permissions(a).contains("orders.execute"))
          people.add(Map.of("id", a.id, "name", a.displayName));
    }
    return Map.of("engineers", people);
  }

  /** 统计当前数据范围的工单指标。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  Map<String, Object> dashboard() {
    Account u = access.require("orders.read");
    String where =
        " where 1=1"
            + (access.admin(u) ? "" : " and orgId=:org")
            + (u.customerId == null ? "" : " and customerId=:customer")
            + (!access.admin(u) && access.permissions(u).contains("orders.assigned")
                ? " and assigneeId=:assignee"
                : "");
    var q = em.createQuery("from WorkOrder" + where, WorkOrder.class);
    if (!access.admin(u)) q.setParameter("org", u.orgId);
    if (u.customerId != null) q.setParameter("customer", u.customerId);
    if (!access.admin(u) && access.permissions(u).contains("orders.assigned"))
      q.setParameter("assignee", u.id);
    List<WorkOrder> list = q.getResultList();
    Map<String, Long> states = new TreeMap<>();
    BigDecimal cost = BigDecimal.ZERO, charge = BigDecimal.ZERO;
    long overdue = 0;
    for (WorkOrder o : list) {
      states.merge(o.state, 1L, Long::sum);
      if (!Set.of("CLOSED", "CANCELLED", "WAIT_PARTS", "WAIT_CUSTOMER").contains(o.state)
          && o.dueAt.isBefore(Orders.now())) overdue++;
      cost =
          cost.add(
              o.partCost.add(
                  o.laborRate
                      .multiply(BigDecimal.valueOf(o.laborMinutes))
                      .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)));
      if (o.state.equals("CLOSED") && o.coverage.equals("CHARGEABLE"))
        charge = charge.add(o.quotedAmount);
    }
    boolean financial = access.permissions(u).contains("report.read");
    return Map.of(
        "total",
        list.size(),
        "states",
        states,
        "overdue",
        overdue,
        "closed",
        states.getOrDefault("CLOSED", 0L),
        "cost",
        financial ? cost : BigDecimal.ZERO,
        "charge",
        financial ? charge : BigDecimal.ZERO,
        "showFinance",
        financial);
  }

  /** 导出限定范围的工单CSV报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports/orders.csv")
  ResponseEntity<String> export(
      @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String state) {
    access.require("report.read");
    StringBuilder out = new StringBuilder("\uFEFF工单编号,标题,状态,设备编号,权益,报价,备件成本,工时分钟,结算\r\n");
    for (int page = 0; page < 50; page++) {
      var result = catalog.list("orders", q, page, 100, "newest", state);
      List<?> rows = (List<?>) result.get("items");
      for (Object row : rows) {
        WorkOrder o = (WorkOrder) row;
        out.append(
                String.join(
                    ",",
                    List.of(
                        csv(o.id),
                        csv(o.name),
                        csv(o.state),
                        csv(o.assetId),
                        csv(o.coverage),
                        csv(o.quotedAmount),
                        csv(o.partCost),
                        csv(o.laborMinutes),
                        csv(o.settlementState))))
            .append("\r\n");
      }
      if (rows.size() < 100) break;
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=service-orders.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(out.toString());
  }

  static String csv(Object value) {
    String s = Objects.toString(value, "");
    if (s.matches("^[=+@-].*")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  /** 返回本地规则建议与已发布知识资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}/advice")
  @Transactional(readOnly = true)
  Object advice(@PathVariable long id, @RequestParam(defaultValue = "") String q) {
    access.require("knowledge.read");
    access.require("orders.read");
    WorkOrder o = access.find(WorkOrder.class, id);
    List<Knowledge> sources =
        q.isBlank()
            ? List.of()
            : em.createQuery(
                    "from Knowledge where orgId=:o and published=true and (name like :q or content like :q) order by updatedAt desc",
                    Knowledge.class)
                .setParameter("o", o.orgId)
                .setParameter(
                    "q",
                    "%"
                        + q.substring(0, Math.min(120, q.length()))
                            .replace("%", "")
                            .replace("_", "")
                        + "%")
                .setMaxResults(5)
                .getResultList();
    return advice.advise(o, sources);
  }
}
