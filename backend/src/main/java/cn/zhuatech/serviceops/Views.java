// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import java.util.*;

/** 客户输出屏蔽内部成本与库存操作元数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
final class Views {
  private Views() {}

  /** 客户响应移除内部计价及操作元数据，员工资料保持业务输出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static Object forUser(Object row, Account user) {
    if (user.customerId == null || !(row instanceof WorkOrder || row instanceof Movement))
      return row;
    Set<String> hidden =
        Set.of("laborRate", "partCost", "unitCost", "actorId", "requestKey", "orgId");
    Map<String, Object> result = new LinkedHashMap<>();
    for (var field : row.getClass().getFields()) {
      if (hidden.contains(field.getName())) continue;
      try {
        result.put(field.getName(), field.get(row));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return result;
  }
}
