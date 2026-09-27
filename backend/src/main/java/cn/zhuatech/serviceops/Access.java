// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 每次业务访问重新校验账号状态、角色与数据范围。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
public class Access {
  final EntityManager em;
  public static final Set<String> PERMISSIONS =
      Set.of(
          "catalog.read",
          "catalog.write",
          "orders.read",
          "orders.create",
          "orders.dispatch",
          "orders.execute",
          "orders.review",
          "orders.confirm",
          "orders.close",
          "orders.cancel",
          "orders.reopen",
          "orders.assigned",
          "inventory.read",
          "inventory.write",
          "plans.manage",
          "knowledge.read",
          "knowledge.write",
          "audit.read",
          "report.read",
          "admin.manage");

  public Access(EntityManager em) {
    this.em = em;
  }

  /** 返回当前有效账号，停用账号的既有会话立即失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account user() {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    Account u =
        em
            .createQuery("from Account where username=:n", Account.class)
            .setParameter("n", name)
            .getResultList()
            .stream()
            .findFirst()
            .orElseThrow(() -> error(401, "请重新登录"));
    if (!u.enabled) throw error(401, "账号已停用");
    if (SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            instanceof LoginPrincipal principal
        && !principal.matches(u)) throw error(401, "密码已修改，请重新登录");
    if (u.customerId != null) {
      Customer c = em.find(Customer.class, u.customerId);
      if (c == null || !c.enabled) throw error(403, "客户已停用");
    }
    if (!em.find(Organization.class, u.orgId).enabled) throw error(403, "部门已停用");
    return u;
  }

  /** 计算服务器上的有效权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Set<String> permissions(Account u) {
    Role role = em.find(Role.class, u.roleId);
    return new HashSet<>(Arrays.asList(role.permissions.split(",")));
  }

  /** 管理权限允许跨部门查看；仍执行关联部门一致性校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean admin(Account u) {
    return permissions(u).contains("admin.manage");
  }

  /** 拒绝缺少业务权限的访问。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account require(String permission) {
    Account u = user();
    if (!permissions(u).contains(permission)) throw error(403, "没有此操作权限");
    return u;
  }

  /** 防止跨部门、跨客户或工程师越权读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Account u, Scoped row) {
    if (!admin(u) && !Objects.equals(u.orgId, row.orgId)) return false;
    if (u.customerId != null) {
      Long customer =
          row instanceof Customer x
              ? x.id
              : row instanceof Asset x
                  ? x.customerId
                  : row instanceof Contract x
                      ? x.customerId
                      : row instanceof WorkOrder x ? x.customerId : null;
      if (customer == null || !customer.equals(u.customerId)) return false;
    }
    if (row instanceof WorkOrder o
        && !admin(u)
        && permissions(u).contains("orders.assigned")
        && !Objects.equals(o.assigneeId, u.id)) return false;
    return true;
  }

  /** 统一可见资源查找，不泄漏其他范围的数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public <T extends Scoped> T find(Class<T> type, long id) {
    T x = em.find(type, id);
    if (x == null || !visible(user(), x)) throw error(404, "记录不存在或不在数据范围内");
    return x;
  }

  /** 构建不包含内部异常细节的业务错误。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static ResponseStatusException error(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }
}
