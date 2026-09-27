// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;
import static cn.zhuatech.serviceops.Catalog.*;
import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 设备权益、服务状态、人工费及结算事务。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Orders {
  final EntityManager em;
  final Access access;
  final Catalog catalog;

  public Orders(EntityManager e, Access a, Catalog c) {
    em = e;
    access = a;
    catalog = c;
  }

  static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }

  /** 建立真实服务请求并固化权益与服务时限；不允许请求者指定其他客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public WorkOrder create(Map<String, Object> m) {
    Account u = access.require("orders.create");
    Asset a = access.find(Asset.class, number(m, "assetId"));
    Customer c = access.find(Customer.class, a.customerId);
    if (!a.enabled || !c.enabled) throw error(400, "客户或设备已停用");
    WorkOrder o = build(a, text(m, "name", true, 200), text(m, "description", true, 2000));
    String p = text(m, "priority", true, 30);
    if (!Set.of("NORMAL", "URGENT").contains(p)) throw error(400, "优先级不存在");
    o.priority = p;
    em.persist(o);
    em.flush();
    catalog.audit(u, "ORDER_CREATE", "orders:" + o.id, "设备:" + a.id + " 权益:" + o.coverage);
    return o;
  }

  WorkOrder build(Asset a, String title, String description) {
    WorkOrder o = new WorkOrder();
    o.orgId = a.orgId;
    o.assetId = a.id;
    o.customerId = a.customerId;
    o.name = title;
    o.description = description;
    o.state = "PENDING";
    o.priority = "NORMAL";
    o.settlementState = "UNSETTLED";
    LocalDate today = now().toLocalDate();
    Contract contract =
        em
            .createQuery(
                "from Contract where assetId=:a and enabled=true and startDate<=:d and endDate>=:d order by endDate desc,id desc",
                Contract.class)
            .setParameter("a", a.id)
            .setParameter("d", today)
            .setMaxResults(1)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    o.contractId = contract == null ? null : contract.id;
    o.coverage =
        contract != null || (a.warrantyUntil != null && !a.warrantyUntil.isBefore(today))
            ? "COVERED"
            : "CHARGEABLE";
    o.quoteApproved = o.coverage.equals("COVERED");
    o.respondBy =
        now()
            .plusHours(
                contract == null
                    ? parameter(a.orgId, "response_hours", "24").intValueExact()
                    : contract.responseHours);
    o.dueAt =
        now()
            .plusHours(
                contract == null
                    ? parameter(a.orgId, "resolution_hours", "72").intValueExact()
                    : contract.resolutionHours);
    o.laborRate = parameter(a.orgId, "labor_rate", "120.00");
    return o;
  }

  /** 系统参数按部门取当前有效值，创建工单时固化，不追溯历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  BigDecimal parameter(Long org, String name, String fallback) {
    String value =
        em
            .createQuery(
                "select c.value from ConfigEntry c where orgId=:o and kind='系统参数' and name=:n and enabled=true order by id desc",
                String.class)
            .setParameter("o", org)
            .setParameter("n", name)
            .setMaxResults(1)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(fallback);
    return new BigDecimal(value);
  }

  /** 对同一工单串行校验版本、权限、状态和必要业务证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public WorkOrder action(long id, String action, Map<String, Object> m) {
    String permission =
        switch (action) {
          case "dispatch", "quote" -> "orders.dispatch";
          case "accept", "wait-parts", "wait-customer", "resume", "finish" -> "orders.execute";
          case "approve", "reject" -> "orders.review";
          case "confirm", "approve-quote" -> "orders.confirm";
          case "settle" -> "orders.close";
          case "cancel" -> "orders.cancel";
          case "reopen" -> "orders.reopen";
          default -> throw error(404, "操作不存在");
        };
    Account u = access.require(permission);
    WorkOrder o = access.find(WorkOrder.class, id);
    em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
    version(o, m);
    if (Set.of("quote", "approve-quote").contains(action)) {
      if (!o.state.equals("PENDING") || !o.coverage.equals("CHARGEABLE"))
        throw error(409, "仅待派工的收费工单可确认报价");
      if (action.equals("quote")) {
        o.quotedAmount = money(m, "quotedAmount");
        if (o.quotedAmount.signum() == 0) throw error(400, "收费报价必须大于0");
        o.reason = text(m, "reason", true, 500);
        o.quoteApproved = false;
      } else {
        if (o.quotedAmount.signum() == 0) throw error(409, "尚未报价");
        o.quoteApproved = true;
      }
    } else if (action.equals("settle")) {
      if (!o.state.equals("CLOSED") || o.settlementState.equals("SETTLED"))
        throw error(409, "仅已关闭且未结算工单可登记结算");
      o.paymentReference = text(m, "paymentReference", true, 120);
      o.settlementState = "SETTLED";
    } else {
      Workflow.check(o.state, action);
      switch (action) {
        case "dispatch" -> {
          if (!o.quoteApproved) throw error(409, "收费工单须先由客户确认报价");
          long aid = number(m, "assigneeId");
          Account engineer = em.find(Account.class, aid);
          if (engineer == null
              || !engineer.enabled
              || engineer.customerId != null
              || !Objects.equals(engineer.orgId, o.orgId)
              || !access.permissions(engineer).contains("orders.execute"))
            throw error(400, "请选择同部门有效工程师");
          o.assigneeId = aid;
          o.state = "DISPATCHED";
        }
        case "accept" -> {
          if (!access.admin(u) && !Objects.equals(o.assigneeId, u.id))
            throw error(403, "仅指派工程师可接单");
          o.state = "IN_PROGRESS";
          o.acceptedAt = now();
        }
        case "wait-parts", "wait-customer" -> {
          o.reason = text(m, "reason", true, 500);
          o.waitingAt = now();
          o.state = action.equals("wait-parts") ? "WAIT_PARTS" : "WAIT_CUSTOMER";
        }
        case "resume" -> {
          if (o.waitingAt != null) o.dueAt = o.dueAt.plus(Duration.between(o.waitingAt, now()));
          o.waitingAt = null;
          o.state = "IN_PROGRESS";
        }
        case "finish" -> {
          o.diagnosis = text(m, "diagnosis", true, 2000);
          o.resolution = text(m, "resolution", true, 2000);
          o.laborMinutes = integer(m, "laborMinutes", 0, 100000);
          o.safetyChecked = flag(m, "safetyChecked", false);
          o.testPassed = flag(m, "testPassed", false);
          if (!o.safetyChecked || !o.testPassed) throw error(400, "须完成安全检查与功能验证");
          if (held(o.id) > 0) throw error(409, "尚有已领备件未消耗或退回");
          o.state = "REVIEW";
          o.resolvedAt = now();
        }
        case "approve" -> o.state = "CONFIRM";
        case "reject" -> {
          o.reason = text(m, "reason", true, 500);
          o.state = "IN_PROGRESS";
          o.testPassed = false;
          o.resolvedAt = null;
        }
        case "confirm" -> {
          o.state = "CLOSED";
          o.closedAt = now();
        }
        case "cancel" -> {
          if (held(o.id) > 0) throw error(409, "取消前须退回已领备件");
          o.reason = text(m, "reason", true, 500);
          o.state = "CANCELLED";
        }
        case "reopen" -> {
          if (o.settlementState.equals("SETTLED")) throw error(409, "已结算工单须新建关联服务请求，不能重开账单");
          o.reason = text(m, "reason", true, 500);
          o.state = "IN_PROGRESS";
          o.closedAt = null;
          o.resolvedAt = null;
          o.testPassed = false;
        }
        default -> throw error(400, "无效操作");
      }
    }
    catalog.audit(u, "ORDER_" + action.toUpperCase(Locale.ROOT), "orders:" + id, "状态:" + o.state);
    em.flush();
    return o;
  }

  long held(Long id) {
    return em.createQuery(
            "select coalesce(sum(case when kind='ISSUE' then quantity when kind='RETURN' or kind='CONSUME' then -quantity else 0 end),0) from Movement where orderId=:o",
            Long.class)
        .setParameter("o", id)
        .getSingleResult();
  }

  /** 完工详情包含受数据权限保护的服务过程、备件、附件和费用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(long id) {
    access.require("orders.read");
    WorkOrder o = access.find(WorkOrder.class, id);
    return Map.of(
        "order",
        Views.forUser(o, access.user()),
        "asset",
        access.find(Asset.class, o.assetId),
        "movements",
        em
            .createQuery("from Movement where orderId=:o order by id", Movement.class)
            .setParameter("o", id)
            .getResultList()
            .stream()
            .map(x -> Views.forUser(x, access.user()))
            .toList(),
        "attachments",
        em.createQuery("from Attachment where orderId=:o order by id", Attachment.class)
            .setParameter("o", id)
            .getResultList(),
        "cost",
        access.user().customerId != null
            ? BigDecimal.ZERO
            : o.partCost.add(
                o.laborRate
                    .multiply(BigDecimal.valueOf(o.laborMinutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)),
        "charge",
        o.coverage.equals("COVERED") ? BigDecimal.ZERO : o.quotedAmount);
  }
}
