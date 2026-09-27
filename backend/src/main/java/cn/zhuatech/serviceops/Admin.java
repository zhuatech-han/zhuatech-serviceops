// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;
import static cn.zhuatech.serviceops.Catalog.*;
import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织、角色和账号维护，授权变更立即作用于业务访问。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Admin {
  final EntityManager em;
  final Access access;
  final PasswordEncoder encoder;
  final Catalog catalog;

  public Admin(EntityManager em, Access a, PasswordEncoder e, Catalog c) {
    this.em = em;
    access = a;
    encoder = e;
    catalog = c;
  }

  /** 返回管理资料，密码散列始终从序列化中排除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> data() {
    access.require("admin.manage");
    return Map.of(
        "users",
        em.createQuery("from Account order by id", Account.class).getResultList(),
        "roles",
        em.createQuery("from Role order by id", Role.class).getResultList(),
        "organizations",
        em.createQuery("from Organization order by id", Organization.class).getResultList(),
        "permissions",
        new TreeSet<>(Access.PERMISSIONS));
  }

  /** 创建或更新组织、角色、账号；禁止管理员锁定自身或降权自身。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String kind, Long id, Map<String, Object> m) {
    Account actor = access.require("admin.manage");
    if (kind.equals("organizations")) {
      Organization x = id == null ? new Organization() : em.find(Organization.class, id);
      if (x == null) throw error(404, "部门不存在");
      if (id != null) version(x, m);
      x.name = text(m, "name", true, 120);
      boolean active = flag(m, "enabled", true);
      if (!active && Objects.equals(actor.orgId, x.id)) throw error(409, "不能停用自己的部门");
      x.enabled = active;
      if (id == null) em.persist(x);
      catalog.audit(actor, "ORG_SAVE", "org:" + x.id, "维护组织");
      return x;
    }
    if (kind.equals("roles")) {
      Role x = id == null ? new Role() : em.find(Role.class, id);
      if (x == null) throw error(404, "角色不存在");
      if (id != null) version(x, m);
      if (x.builtin && x.name.equals("管理员")) throw error(409, "内置管理员权限不能修改");
      String p = text(m, "permissions", true, 1800);
      Set<String> ps = new HashSet<>(Arrays.asList(p.split(",")));
      if (!Access.PERMISSIONS.containsAll(ps)) throw error(400, "权限不存在");
      if (ps.contains("orders.assigned") && ps.contains("admin.manage"))
        throw error(400, "管理员不能使用工程师限定范围");
      if (x.builtin && !ps.equals(new HashSet<>(Arrays.asList(x.permissions.split(",")))))
        throw error(409, "内置角色保留既定权限，请新建自定义角色");
      if (x.builtin && !x.name.equals(text(m, "name", true, 120))) throw error(409, "内置角色名称不能修改");
      x.name = text(m, "name", true, 120);
      x.permissions = String.join(",", new TreeSet<>(ps));
      if (id == null) em.persist(x);
      catalog.audit(actor, "ROLE_SAVE", "role:" + x.id, "维护角色权限");
      return x;
    }
    if (!kind.equals("users")) throw error(404, "管理模块不存在");
    Account x = id == null ? new Account() : em.find(Account.class, id);
    if (x == null) throw error(404, "账号不存在");
    if (id != null) version(x, m);
    long orgId = number(m, "orgId"), roleId = number(m, "roleId");
    Organization org = em.find(Organization.class, orgId);
    Role role = em.find(Role.class, roleId);
    if (org == null || !org.enabled || role == null) throw error(400, "部门或角色不可用");
    boolean enabled = flag(m, "enabled", true);
    String username = text(m, "username", true, 120);
    if (!username.matches("[a-zA-Z0-9_.-]{3,64}")) throw error(400, "账号使用3至64位字母、数字或 ._- ");
    if (Objects.equals(x.id, actor.id)
        && (!enabled
            || !Objects.equals(x.roleId, roleId)
            || !Objects.equals(x.orgId, orgId)
            || !x.username.equals(username))) throw error(409, "不能停用、转移或降权当前管理员");
    Long customerId =
        m.get("customerId") == null || m.get("customerId").toString().isBlank()
            ? null
            : number(m, "customerId");
    if (customerId != null) {
      Customer c = em.find(Customer.class, customerId);
      if (c == null
          || !c.enabled
          || !Objects.equals(c.orgId, orgId)
          || Arrays.asList(role.permissions.split(",")).stream()
              .anyMatch(
                  v ->
                      !Set.of("catalog.read", "orders.read", "orders.create", "orders.confirm")
                          .contains(v))) throw error(400, "客户账号必须绑定有效的同部门客户和仅客户权限");
    } else if (role.name.equals("客户")) throw error(400, "客户账号必须绑定客户");
    String password = text(m, "password", id == null, 128);
    if (!password.isEmpty()) {
      if (password.length() < 12) throw error(400, "密码至少12位");
      x.passwordHash = encoder.encode(password);
    }
    x.username = username;
    x.displayName = text(m, "displayName", true, 120);
    x.orgId = orgId;
    x.roleId = roleId;
    x.customerId = customerId;
    x.enabled = enabled;
    if (id == null) em.persist(x);
    em.flush();
    catalog.audit(actor, "ACCOUNT_SAVE", "user:" + x.id, "维护账号状态与授权");
    return x;
  }

  /** 校验旧密码再更新散列，调用方随后销毁当前会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void password(Map<String, Object> m) {
    Account u = access.user();
    String old = text(m, "oldPassword", true, 128), next = text(m, "newPassword", true, 128);
    if (!encoder.matches(old, u.passwordHash)) throw error(400, "旧密码不正确");
    if (next.length() < 12) throw error(400, "新密码至少12位");
    u.passwordHash = encoder.encode(next);
    catalog.audit(u, "PASSWORD_CHANGE", "user:" + u.id, "修改个人密码");
  }
}
