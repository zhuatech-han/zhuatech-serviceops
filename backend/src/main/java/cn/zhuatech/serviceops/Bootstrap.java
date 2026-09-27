// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化组织、角色和管理员，不重设已有密码。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements CommandLineRunner {
  final EntityManager em;
  final PasswordEncoder encoder;
  final String password;

  public Bootstrap(
      EntityManager em,
      PasswordEncoder encoder,
      @Value("${serviceops.admin-password}") String password) {
    this.em = em;
    this.encoder = encoder;
    this.password = password;
  }

  /** 幂等初始化，业务资料由操作者录入；不存在共享默认生产密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(String... args) {
    if (em.createQuery("select count(a) from Account a", Long.class).getSingleResult() > 0) return;
    if (password.length() < 12) throw new IllegalArgumentException("初始化管理员密码必须至少 12 位");
    Organization org = new Organization();
    org.name = "服务运营部";
    org.enabled = true;
    em.persist(org);
    Map<String, String> specs = new LinkedHashMap<>();
    specs.put("管理员", String.join(",", new TreeSet<>(Access.PERMISSIONS)));
    specs.put(
        "服务经理",
        "catalog.read,catalog.write,orders.read,orders.create,orders.dispatch,orders.review,orders.close,orders.cancel,orders.reopen,inventory.read,plans.manage,knowledge.read,knowledge.write,audit.read,report.read");
    specs.put(
        "调度",
        "catalog.read,catalog.write,orders.read,orders.create,orders.dispatch,orders.cancel,inventory.read,plans.manage,knowledge.read");
    specs.put(
        "工程师",
        "catalog.read,orders.read,orders.assigned,orders.execute,inventory.read,knowledge.read");
    specs.put("仓库", "inventory.read,inventory.write,orders.read");
    specs.put("客户", "catalog.read,orders.read,orders.create,orders.confirm");
    Long adminRole = null;
    for (var e : specs.entrySet()) {
      Role role = new Role();
      role.name = e.getKey();
      role.permissions = e.getValue();
      role.builtin = true;
      em.persist(role);
      if (role.name.equals("管理员")) adminRole = role.id;
    }
    Account u = new Account();
    u.username = "admin";
    u.displayName = "系统管理员";
    u.passwordHash = encoder.encode(password);
    u.orgId = org.id;
    u.roleId = adminRole;
    u.enabled = true;
    em.persist(u);
    for (var parameter :
        Map.of("labor_rate", "120.00", "response_hours", "24", "resolution_hours", "72")
            .entrySet()) {
      ConfigEntry x = new ConfigEntry();
      x.orgId = org.id;
      x.kind = "系统参数";
      x.name = parameter.getKey();
      x.value = parameter.getValue();
      x.enabled = true;
      em.persist(x);
    }
    for (String value : List.of("设备故障", "安装调试", "定期维保")) {
      ConfigEntry x = new ConfigEntry();
      x.orgId = org.id;
      x.kind = "故障分类";
      x.name = value;
      x.value = value;
      x.enabled = true;
      em.persist(x);
    }
  }
}
