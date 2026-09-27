// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;
import static cn.zhuatech.serviceops.Catalog.*;
import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 备件流水事务与请求幂等，每次退回或消耗关联原领用流水。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Inventory {
  final EntityManager em;
  final Access access;
  final Catalog catalog;

  public Inventory(EntityManager e, Access a, Catalog c) {
    em = e;
    access = a;
    catalog = c;
  }

  /** 原子调整库存和工单耗材成本；重复键仅返回相同业务结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Movement move(Map<String, Object> m) {
    String kind = text(m, "kind", true, 30);
    if (!Set.of("RECEIVE", "ISSUE", "RETURN", "CONSUME").contains(kind))
      throw error(400, "库存操作不存在");
    Account u = access.require(kind.equals("CONSUME") ? "orders.execute" : "inventory.write");
    long partId = number(m, "partId");
    int qty = integer(m, "quantity", 1, 1000000);
    String key = text(m, "requestKey", true, 120);
    Long orderId = kind.equals("RECEIVE") ? null : number(m, "orderId");
    Long issueId = Set.of("RETURN", "CONSUME").contains(kind) ? number(m, "issueId") : null;
    WorkOrder order = orderId == null ? null : access.find(WorkOrder.class, orderId);
    if (order != null) em.refresh(order, LockModeType.PESSIMISTIC_WRITE);
    Part p = access.find(Part.class, partId);
    em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
    if (order != null) sameOrg(order, p);
    var previous =
        em
            .createQuery("from Movement where orgId=:org and requestKey=:key", Movement.class)
            .setParameter("org", p.orgId)
            .setParameter("key", key)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    if (previous != null) {
      if (!previous.kind.equals(kind)
          || previous.quantity != qty
          || !Objects.equals(previous.partId, partId)
          || !Objects.equals(previous.orderId, orderId)
          || !Objects.equals(previous.issueId, issueId)) throw error(409, "幂等键已用于其他库存操作");
      return previous;
    }
    if (!p.enabled) throw error(400, "备件已停用");
    if (order != null
        && !Set.of("DISPATCHED", "IN_PROGRESS", "WAIT_PARTS", "WAIT_CUSTOMER")
            .contains(order.state)) throw error(409, "当前工单状态不能处理备件");
    Movement x = new Movement();
    x.orgId = p.orgId;
    x.partId = p.id;
    x.kind = kind;
    x.orderId = orderId;
    x.issueId = issueId;
    x.quantity = qty;
    x.requestKey = key;
    x.actorId = u.id;
    x.unitCost = p.unitCost;
    x.reason = text(m, "reason", true, 500);
    if (kind.equals("RECEIVE")) {
      if ((long) p.stock + qty > 1000000000L) throw error(400, "库存超过上限");
      p.stock += qty;
    }
    if (kind.equals("ISSUE")) {
      if (p.stock < qty) throw error(409, "库存不足");
      p.stock -= qty;
    }
    if (issueId != null) {
      Movement issue = access.find(Movement.class, issueId);
      if (!issue.kind.equals("ISSUE")
          || !Objects.equals(issue.orderId, orderId)
          || !Objects.equals(issue.partId, partId)) throw error(400, "原领用流水不匹配");
      long used =
          em.createQuery(
                  "select coalesce(sum(quantity),0) from Movement where issueId=:i", Long.class)
              .setParameter("i", issueId)
              .getSingleResult();
      if (used + qty > issue.quantity) throw error(409, "数量超过领用余量");
      x.unitCost = issue.unitCost;
      if (kind.equals("RETURN")) {
        if ((long) p.stock + qty > 1000000000L) throw error(400, "库存超过上限");
        p.stock += qty;
      } else order.partCost = order.partCost.add(x.unitCost.multiply(BigDecimal.valueOf(qty)));
    }
    em.persist(x);
    catalog.audit(
        u, "PART_" + kind, "part:" + p.id, "数量:" + qty + " 工单:" + Objects.toString(orderId, "无"));
    em.flush();
    return x;
  }
}
