// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Models.*;

import java.util.*;
import org.springframework.stereotype.Component;

/** 服务建议适配接口，不授予自动执行业务的权限。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
public interface AdviceAdapter {
  /** 返回明示来源模式的建议，不改变工单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Map<String, Object> advise(WorkOrder order, List<Knowledge> sources);
}

/** 无凭证可用的本地规则适配器，不能冒充大模型结果。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Component
class LocalAdvice implements AdviceAdapter {
  /** 按当前状态提供检查提示和已授权知识来源，不执行任何业务修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  public Map<String, Object> advise(WorkOrder o, List<Knowledge> sources) {
    List<String> checks = new ArrayList<>();
    if (o.state.equals("PENDING")) checks.add(o.quoteApproved ? "核对工程师技能与工作量后派工" : "先填写报价并取得客户确认");
    if (o.state.equals("IN_PROGRESS")) {
      checks.add("记录故障诊断和处理结果");
      checks.add("核对领用备件，消耗或退回后提交复核");
    }
    if (o.state.equals("REVIEW")) checks.add("复核安全检查、功能验证与耗材记录");
    if (o.state.equals("CONFIRM")) checks.add("等待客户确认服务结果");
    return Map.of(
        "mode", "LOCAL_RULES", "checks", checks, "sources", sources, "changesBusinessData", false);
  }
}
