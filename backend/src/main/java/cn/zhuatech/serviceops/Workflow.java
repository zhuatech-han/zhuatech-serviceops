// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;

import java.util.*;

/** 明确工单可执行状态，禁止越过接单、复核与客户确认。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
public final class Workflow {
  private Workflow() {}

  static final Map<String, Set<String>> SOURCES =
      Map.ofEntries(
          Map.entry("dispatch", Set.of("PENDING", "DISPATCHED")),
          Map.entry("accept", Set.of("DISPATCHED")),
          Map.entry("wait-parts", Set.of("IN_PROGRESS")),
          Map.entry("wait-customer", Set.of("IN_PROGRESS")),
          Map.entry("resume", Set.of("WAIT_PARTS", "WAIT_CUSTOMER")),
          Map.entry("finish", Set.of("IN_PROGRESS")),
          Map.entry("approve", Set.of("REVIEW")),
          Map.entry("reject", Set.of("REVIEW", "CONFIRM")),
          Map.entry("confirm", Set.of("CONFIRM")),
          Map.entry(
              "cancel",
              Set.of("PENDING", "DISPATCHED", "IN_PROGRESS", "WAIT_PARTS", "WAIT_CUSTOMER")),
          Map.entry("reopen", Set.of("CLOSED")));

  /** 校验命令合法性，实际字段与权限由事务服务检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void check(String state, String action) {
    if (!SOURCES.getOrDefault(action, Set.of()).contains(state)) throw error(409, "当前状态不能执行此操作");
  }
}
