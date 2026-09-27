// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 周期维保按计划日期生成工单，数据库唯一键保证重复触发不重复创建。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
public class Maintenance {
  final EntityManager em;
  final Orders orders;
  final Access access;
  final Catalog catalog;

  public Maintenance(EntityManager e, Orders o, Access a, Catalog c) {
    em = e;
    orders = o;
    access = a;
    catalog = c;
  }

  /** 管理人员触发到期计划，记录执行结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public int trigger() {
    Account u = access.require("plans.manage");
    int result = generate(u.orgId, access.admin(u));
    catalog.audit(u, "PLAN_TRIGGER", "plans", "生成工单:" + result);
    return result;
  }

  /** 自动生成到期维保任务，不自动派工或完成；最多追补每计划30期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(
      fixedDelayString = "${serviceops.schedule-delay:60000}",
      initialDelayString = "${serviceops.schedule-initial-delay:60000}")
  @Transactional
  public void scheduled() {
    generate(null, true);
  }

  int generate(Long org, boolean all) {
    var query =
        em.createQuery(
                "from Plan where enabled=true and nextDate<=:d"
                    + (all ? "" : " and orgId=:o")
                    + " order by id",
                Plan.class)
            .setParameter("d", LocalDate.now(ZoneOffset.UTC));
    if (!all) query.setParameter("o", org);
    List<Plan> plans = query.getResultList();
    int count = 0;
    for (Plan p : plans) {
      if (!all && !Objects.equals(p.orgId, org)) continue;
      em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
      Asset a = em.find(Asset.class, p.assetId);
      Customer c = em.find(Customer.class, a.customerId);
      if (!a.enabled || !c.enabled || !em.find(Organization.class, p.orgId).enabled) continue;
      for (int n = 0; n < 30 && !p.nextDate.isAfter(LocalDate.now(ZoneOffset.UTC)); n++) {
        long exists =
            em.createQuery(
                    "select count(o) from WorkOrder o where planId=:p and plannedDate=:d",
                    Long.class)
                .setParameter("p", p.id)
                .setParameter("d", p.nextDate)
                .getSingleResult();
        if (exists == 0) {
          WorkOrder o = orders.build(a, p.name, p.description);
          o.planId = p.id;
          o.plannedDate = p.nextDate;
          em.persist(o);
          Audit audit = new Audit();
          audit.orgId = p.orgId;
          audit.actor = "system";
          audit.action = "PLAN_GENERATE";
          audit.resource = "plans:" + p.id;
          audit.detail = "计划日期:" + p.nextDate + " 工单:" + o.id;
          em.persist(audit);
          count++;
        }
        p.nextDate = p.nextDate.plusDays(p.intervalDays);
      }
    }
    return count;
  }
}
