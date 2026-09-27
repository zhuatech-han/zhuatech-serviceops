// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;
import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 主数据维护、搜索分页和关联范围校验。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Catalog {
  final EntityManager em;
  final Access access;
  static final Map<String, Class<? extends Scoped>> TYPES =
      Map.of(
          "customers",
          Customer.class,
          "assets",
          Asset.class,
          "contracts",
          Contract.class,
          "parts",
          Part.class,
          "plans",
          Plan.class,
          "knowledge",
          Knowledge.class,
          "config",
          ConfigEntry.class,
          "orders",
          WorkOrder.class,
          "movements",
          Movement.class,
          "audit",
          Audit.class);

  public Catalog(EntityManager em, Access access) {
    this.em = em;
    this.access = access;
  }

  static String permission(String kind, boolean write) {
    return switch (kind) {
      case "parts", "movements" -> write ? "inventory.write" : "inventory.read";
      case "plans" -> "plans.manage";
      case "knowledge" -> write ? "knowledge.write" : "knowledge.read";
      case "config" -> "admin.manage";
      case "orders" -> write ? "orders.create" : "orders.read";
      case "audit" -> "audit.read";
      default -> write ? "catalog.write" : "catalog.read";
    };
  }

  /** 按可见范围返回稳定分页，搜索和排序仅使用白名单字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      String kind, String query, int page, int size, String sort, String state) {
    Class<? extends Scoped> type = TYPES.get(kind);
    if (type == null) throw error(404, "模块不存在");
    Account u = access.require(permission(kind, false));
    if (page < 0 || page > 1000000 || size < 1 || size > 100) throw error(400, "分页范围不正确");
    StringBuilder where = new StringBuilder(" where 1=1");
    Map<String, Object> params = new HashMap<>();
    if (!access.admin(u)) {
      where.append(" and r.orgId=:org");
      params.put("org", u.orgId);
    }
    if (u.customerId != null) {
      String field =
          kind.equals("customers")
              ? "id"
              : Set.of("assets", "contracts", "orders").contains(kind) ? "customerId" : null;
      if (field == null) throw error(403, "此模块不向客户开放");
      where.append(" and r.").append(field).append("=:customer");
      params.put("customer", u.customerId);
    }
    if (kind.equals("orders")
        && !access.admin(u)
        && access.permissions(u).contains("orders.assigned")) {
      where.append(" and r.assigneeId=:assignee");
      params.put("assignee", u.id);
    }
    if (kind.equals("knowledge") && !access.permissions(u).contains("knowledge.write"))
      where.append(" and r.published=true");
    if (query != null && !query.isBlank()) {
      if (query.length() > 120) throw error(400, "搜索词过长");
      String field = kind.equals("audit") ? "detail" : kind.equals("movements") ? "reason" : "name";
      where.append(" and lower(r.").append(field).append(") like :q escape '!' ");
      params.put(
          "q",
          "%"
              + query
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    if (kind.equals("orders") && state != null && !state.isBlank()) {
      where.append(" and r.state=:state");
      params.put("state", state);
    }
    String order =
        switch (sort) {
          case "oldest" -> "id asc";
          case "updated" -> "updatedAt desc, r.id desc";
          default -> "id desc";
        };
    var data =
        em.createQuery(
            "from " + type.getSimpleName() + " r" + where + " order by r." + order, type);
    var count =
        em.createQuery("select count(r) from " + type.getSimpleName() + " r" + where, Long.class);
    params.forEach(
        (k, v) -> {
          data.setParameter(k, v);
          count.setParameter(k, v);
        });
    return Map.of(
        "items",
        data.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(x -> Views.forUser(x, u))
            .toList(),
        "total",
        count.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 创建或更新主数据，仅允许业务白名单字段；库存和工单状态由事务接口管理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Scoped save(String kind, Long id, Map<String, Object> input) {
    Class<? extends Scoped> type = TYPES.get(kind);
    if (type == null || Set.of("orders", "movements", "audit").contains(kind))
      throw error(404, "不支持此维护接口");
    Account u = access.require(permission(kind, true));
    Scoped x;
    if (id == null) {
      try {
        x = type.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      x.orgId = u.orgId;
      if (access.admin(u) && input.get("orgId") != null) {
        x.orgId = number(input, "orgId");
        Organization org = em.find(Organization.class, x.orgId);
        if (org == null || !org.enabled) throw error(400, "部门不存在或已停用");
      }
    } else {
      x = access.find(type, id);
      version(x, input);
    }
    if (x instanceof Customer c) {
      c.name = text(input, "name", true, 200);
      c.site = text(input, "site", false, 500);
      c.contact = text(input, "contact", false, 200);
      c.enabled = flag(input, "enabled", true);
    }
    if (x instanceof Asset a) {
      Customer c = access.find(Customer.class, number(input, "customerId"));
      if (!c.enabled) throw error(400, "客户已停用");
      sameOrg(x, c);
      a.customerId = c.id;
      a.name = text(input, "name", true, 200);
      a.serialNumber = text(input, "serialNumber", true, 120);
      a.location = text(input, "location", false, 500);
      a.warrantyUntil = date(input, "warrantyUntil", false);
      a.enabled = flag(input, "enabled", true);
      if (id != null
          && em.createQuery("select count(o) from WorkOrder o where o.assetId=:a", Long.class)
                  .setParameter("a", id)
                  .getSingleResult()
              > 0
          && !Objects.equals(
              c.id,
              em.createQuery("select o.customerId from WorkOrder o where o.assetId=:a", Long.class)
                  .setParameter("a", id)
                  .setMaxResults(1)
                  .getSingleResult())) throw error(409, "有服务历史的设备不能转移到其他客户");
    }
    if (x instanceof Contract c) {
      Asset a = access.find(Asset.class, number(input, "assetId"));
      sameOrg(x, a);
      c.assetId = a.id;
      c.customerId = a.customerId;
      c.name = text(input, "name", true, 200);
      c.startDate = date(input, "startDate", true);
      c.endDate = date(input, "endDate", true);
      if (c.endDate.isBefore(c.startDate)) throw error(400, "合同结束日期早于开始日期");
      c.responseHours = integer(input, "responseHours", 1, 8760);
      c.resolutionHours = integer(input, "resolutionHours", c.responseHours, 8760);
      c.enabled = flag(input, "enabled", true);
    }
    if (x instanceof Part p) {
      p.name = text(input, "name", true, 200);
      p.sku = text(input, "sku", true, 120);
      p.unit = text(input, "unit", true, 30);
      p.unitCost = money(input, "unitCost");
      p.enabled = flag(input, "enabled", true);
    }
    if (x instanceof Plan p) {
      Asset a = access.find(Asset.class, number(input, "assetId"));
      sameOrg(x, a);
      if (!a.enabled) throw error(400, "设备已停用");
      p.assetId = a.id;
      p.name = text(input, "name", true, 200);
      p.nextDate = date(input, "nextDate", true);
      p.intervalDays = integer(input, "intervalDays", 1, 3650);
      p.description = text(input, "description", true, 2000);
      p.enabled = flag(input, "enabled", true);
    }
    if (x instanceof Knowledge k) {
      k.name = text(input, "name", true, 200);
      k.category = text(input, "category", true, 120);
      k.content = text(input, "content", true, 2000);
      k.published = flag(input, "published", false);
    }
    if (x instanceof ConfigEntry c) {
      c.name = text(input, "name", true, 120);
      c.kind = text(input, "kind", true, 120);
      c.value = text(input, "value", true, 500);
      if (c.kind.equals("系统参数")) {
        if (c.name.equals("labor_rate")) c.value = money(input, "value").toPlainString();
        else if (Set.of("response_hours", "resolution_hours").contains(c.name))
          c.value = Integer.toString(integer(input, "value", 1, 8760));
        else throw error(400, "系统参数仅支持 labor_rate、response_hours、resolution_hours");
        long duplicate =
            em.createQuery(
                    "select count(c) from ConfigEntry c where orgId=:o and kind='系统参数' and name=:n and id<>:i",
                    Long.class)
                .setParameter("o", x.orgId)
                .setParameter("n", c.name)
                .setParameter("i", id == null ? -1L : id)
                .getSingleResult();
        if (duplicate > 0) throw error(409, "此部门的系统参数已存在，请编辑原记录");
      }
      c.enabled = flag(input, "enabled", true);
    }
    if (id == null) em.persist(x);
    em.flush();
    audit(u, id == null ? "CREATE" : "UPDATE", kind + ":" + x.id, "维护业务资料");
    return x;
  }

  /** 停用主数据保留历史，不物理删除关联记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void disable(String kind, long id, long version) {
    access.require(permission(kind, true));
    var type = TYPES.get(kind);
    if (type == null || Set.of("orders", "audit", "movements").contains(kind))
      throw error(400, "记录不能删除");
    var row = access.find(type, id);
    if (row.version != version) throw error(409, "版本已变更");
    try {
      if (row instanceof Knowledge k) k.published = false;
      else type.getField("enabled").set(row, false);
    } catch (ReflectiveOperationException e) {
      throw error(400, "记录不能停用");
    }
    audit(access.user(), "DISABLE", kind + ":" + id, "保留历史并停用");
  }

  /** 记录操作和状态审计，不记录密码或原始敏感请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void audit(Account u, String action, String resource, String detail) {
    Audit a = new Audit();
    a.orgId = u.orgId;
    a.actor = u.username;
    a.action = action;
    a.resource = resource;
    a.detail = detail;
    em.persist(a);
  }

  static void sameOrg(Scoped a, Scoped b) {
    if (!Objects.equals(a.orgId, b.orgId)) throw error(400, "关联资料必须属于同一部门");
  }

  static void version(Base row, Map<String, Object> data) {
    if (!data.containsKey("version")
        || Long.parseLong(Objects.toString(data.get("version"), "")) != row.version)
      throw error(409, "记录已变更，请刷新后重试");
  }

  static String text(Map<String, Object> m, String key, boolean required, int max) {
    String v = Objects.toString(m.get(key), "").trim();
    if ((required && v.isEmpty()) || v.length() > max) throw error(400, key + " 未填写或超过长度限制");
    return v;
  }

  static long number(Map<String, Object> m, String key) {
    try {
      long v = Long.parseLong(Objects.toString(m.get(key), ""));
      if (v < 1) throw new NumberFormatException();
      return v;
    } catch (NumberFormatException e) {
      throw error(400, key + " 必须是有效编号");
    }
  }

  static int integer(Map<String, Object> m, String key, int min, int max) {
    try {
      int v = Integer.parseInt(Objects.toString(m.get(key), ""));
      if (v < min || v > max) throw new NumberFormatException();
      return v;
    } catch (NumberFormatException e) {
      throw error(400, key + " 超出有效范围");
    }
  }

  static boolean flag(Map<String, Object> m, String key, boolean fallback) {
    if (!m.containsKey(key)) return fallback;
    if (m.get(key) instanceof Boolean b) return b;
    throw error(400, key + " 必须是布尔值");
  }

  static LocalDate date(Map<String, Object> m, String key, boolean required) {
    String s = text(m, key, required, 20);
    try {
      return s.isEmpty() ? null : LocalDate.parse(s);
    } catch (DateTimeException e) {
      throw error(400, key + " 日期格式不正确");
    }
  }

  static BigDecimal money(Map<String, Object> m, String key) {
    try {
      BigDecimal v = new BigDecimal(Objects.toString(m.get(key), "0"));
      if (v.signum() < 0 || v.compareTo(new BigDecimal("10000000")) > 0 || v.scale() > 2)
        throw new NumberFormatException();
      return v.setScale(2);
    } catch (NumberFormatException e) {
      throw error(400, key + " 金额须为非负且最多两位小数");
    }
  }
}
