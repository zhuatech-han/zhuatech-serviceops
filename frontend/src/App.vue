<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信：zhuatech / zhuatech2 -->
<script setup>
import { ref, reactive, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { request, importRows } from "./api.js";
import {
  schemas,
  columns,
  menus,
  statuses,
  permissionNames,
} from "./schema.js";
const route = useRoute(),
  router = useRouter();
const me = ref(null),
  permissions = ref([]),
  initial = ref(true),
  busy = ref(false),
  message = ref(""),
  error = ref("");
const username = ref(""),
  password = ref(""),
  q = ref(""),
  state = ref(""),
  sort = ref("newest"),
  page = ref(0),
  rows = ref([]),
  total = ref(0),
  stats = ref(null),
  detail = ref(null),
  advice = ref(null);
const refs = reactive({
  customers: [],
  assets: [],
  parts: [],
  orders: [],
  engineers: [],
  users: [],
  roles: [],
  organizations: [],
});
const modal = ref(null),
  fields = ref([]),
  form = reactive({}),
  importText = ref(""),
  oldPassword = ref(""),
  newPassword = ref("");
let loadId = 0;
const current = computed(() => String(route.params.page || "dashboard"));
const navigation = computed(() =>
  menus.filter((m) => m[2] === "总览" || can(m[2])),
);
const title = computed(
  () => menus.find((m) => m[0] === current.value)?.[1] || "工作台",
);
const currentColumns = computed(() => columns[current.value] || []);
const exportUrl = computed(
  () =>
    "/api/reports/orders.csv?" +
    new URLSearchParams({ q: q.value, state: state.value }),
);
const adminPage = computed(() =>
  ["users", "roles", "organizations"].includes(current.value),
);
const writable = computed(() =>
  adminPage.value
    ? can("admin.manage")
    : can(
        current.value === "parts"
          ? "inventory.write"
          : current.value === "plans"
            ? "plans.manage"
            : current.value === "knowledge"
              ? "knowledge.write"
              : current.value === "config"
                ? "admin.manage"
                : current.value === "orders"
                  ? "orders.create"
                  : "catalog.write",
      ),
);
function can(p) {
  return permissions.value.includes(p);
}
function label(v) {
  if (v === null || v === undefined || v === "") return "—";
  if (typeof v === "boolean") return v ? "是" : "否";
  return statuses[v] || v;
}
function time(v) {
  if (!v) return "—";
  if (String(v).includes("T"))
    return new Date(v + "Z").toLocaleString("zh-CN", { hour12: false });
  return v;
}
function money(v) {
  return Number(v || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
function display(row, key) {
  if (key === "permissions")
    return String(row[key] || "")
      .split(",")
      .filter(
        (p) =>
          p !== "orders.assigned" || !String(row[key]).includes("admin.manage"),
      )
      .map((p) => permissionNames[p] || p)
      .join("、");
  if (key === "enabled") return row.enabled ? "启用" : "停用";
  if (key.endsWith("At")) return time(row[key]);
  return label(row[key]);
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  message.value = "";
  try {
    await fn();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) {
      me.value = null;
      detail.value = null;
    }
  } finally {
    busy.value = false;
  }
}
async function login() {
  await run(async () => {
    await request("/auth/login", {
      method: "POST",
      body: new URLSearchParams({
        username: username.value,
        password: password.value,
      }),
    });
    password.value = "";
    await session();
  });
}
async function session() {
  const data = await request("/auth/me");
  me.value = data.user;
  permissions.value = data.permissions;
  await loadRefs();
  if (!navigation.value.some((item) => item[0] === current.value))
    await router.replace("/" + navigation.value[0][0]);
  await load();
}
async function logout() {
  await run(async () => {
    await request("/auth/logout", { method: "POST" });
    me.value = null;
    detail.value = null;
  });
}
async function loadRefs() {
  const wanted = [];
  if (can("catalog.read")) wanted.push("customers", "assets");
  if (can("inventory.read")) wanted.push("parts");
  if (can("orders.read")) wanted.push("orders");
  for (const kind of wanted) {
    refs[kind] = (await request("/catalog/" + kind + "?size=100")).items;
  }
  refs.engineers = (await request("/lookups")).engineers;
  if (can("admin.manage")) {
    const data = await request("/admin");
    for (const k of ["users", "roles", "organizations"]) refs[k] = data[k];
  }
}
async function load() {
  if (!me.value) return;
  const id = ++loadId,
    p = current.value;
  rows.value = [];
  total.value = 0;
  detail.value = null;
  advice.value = null;
  if (p === "account") return;
  let result;
  if (p === "dashboard") {
    result = await request("/dashboard");
    if (id !== loadId) return;
    stats.value = result;
    if (can("orders.read")) {
      const recent = await request("/catalog/orders?size=6");
      if (id === loadId) rows.value = recent.items;
    }
    return;
  }
  if (adminPage.value) {
    const data = await request("/admin");
    if (id !== loadId) return;
    let dataRows = data[p] || [];
    dataRows.sort((a, b) =>
      sort.value === "oldest"
        ? a.id - b.id
        : sort.value === "updated"
          ? b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id
          : b.id - a.id,
    );
    if (q.value)
      dataRows = dataRows.filter((r) =>
        JSON.stringify(r).toLowerCase().includes(q.value.toLowerCase()),
      );
    total.value = dataRows.length;
    rows.value = dataRows.slice(page.value * 20, (page.value + 1) * 20);
    return;
  }
  result = await request(
    "/catalog/" +
      p +
      "?" +
      new URLSearchParams({
        q: q.value,
        state: state.value,
        sort: sort.value,
        page: page.value,
        size: 20,
      }),
  );
  if (id !== loadId) return;
  rows.value = result.items;
  total.value = result.total;
}
watch(current, () => {
  q.value = "";
  state.value = "";
  page.value = 0;
  error.value = "";
  message.value = "";
  rows.value = [];
  detail.value = null;
  modal.value = null;
  const target = current.value;
  load().catch((e) => {
    if (current.value === target) {
      error.value = e.message;
      if (e.status === 401) me.value = null;
    }
  });
});
async function search() {
  page.value = 0;
  await run(load);
}
async function paginate(delta) {
  page.value += delta;
  await run(load);
}
function clearForm() {
  for (const key of Object.keys(form)) delete form[key];
}
function openEditor(row = null) {
  clearForm();
  const kind = current.value;
  fields.value = [...(schemas[kind] || [])];
  if (
    can("admin.manage") &&
    ["customers", "parts", "knowledge", "config"].includes(kind)
  )
    fields.value.unshift({
      key: "orgId",
      label: "所属部门",
      type: "organizations",
      required: true,
    });
  Object.assign(
    form,
    {
      enabled: true,
      published: false,
      priority: "NORMAL",
      unit: "件",
      unitCost: 0,
      responseHours: 24,
      resolutionHours: 72,
      intervalDays: 30,
      orgId: me.value.orgId,
      nextDate: new Date().toISOString().slice(0, 10),
    },
    row || {},
  );
  form.password = "";
  modal.value = { type: "edit", kind, id: row?.id };
}
function openCommand(action, row, extra = {}) {
  clearForm();
  Object.assign(form, {
    version: row.version,
    quantity: 1,
    requestKey: crypto.randomUUID(),
    reason: "",
    safetyChecked: false,
    testPassed: false,
    laborMinutes: 0,
    ...extra,
  });
  const f = (key, label, type = "text") => ({
    key,
    label,
    type,
    required: true,
  });
  const specs = {
    dispatch: [f("assigneeId", "指派工程师", "engineers")],
    quote: [
      f("quotedAmount", "确认报价（元）", "number"),
      f("reason", "报价说明", "textarea"),
    ],
    finish: [
      f("diagnosis", "故障诊断", "textarea"),
      f("resolution", "处理与验证结果", "textarea"),
      f("laborMinutes", "工时（分钟）", "number"),
      f("safetyChecked", "完成安全检查", "checkbox"),
      f("testPassed", "功能验证通过", "checkbox"),
    ],
    settle: [f("paymentReference", "已完成收款的凭证号")],
    receive: [f("quantity", "入库数量", "number"), f("reason", "入库原因")],
    issue: [
      f("orderId", "领用工单编号", "orders"),
      f("quantity", "领用数量", "number"),
      f("reason", "领用原因"),
    ],
    consume: [f("quantity", "消耗数量", "number"), f("reason", "使用说明")],
    return: [f("quantity", "退回数量", "number"), f("reason", "退回原因")],
  };
  fields.value =
    specs[action] ||
    (["wait-parts", "wait-customer", "cancel", "reject", "reopen"].includes(
      action,
    )
      ? [f("reason", "原因", "textarea")]
      : []);
  modal.value = {
    type: "command",
    action,
    kind: "orders",
    id: row.id,
    name:
      actions.find((a) => a.key === action)?.name ||
      {
        receive: "备件入库",
        issue: "备件领用",
        consume: "消耗备件",
        return: "退回备件",
      }[action],
  };
}
async function submit() {
  await run(async () => {
    const m = modal.value,
      payload = { ...form };
    if (m.type === "edit") {
      for (const field of fields.value) {
        if (
          ["customers", "assets", "organizations", "roles"].includes(
            field.type,
          ) &&
          payload[field.key] === ""
        )
          payload[field.key] = null;
      }
      if (["assets", "contracts", "plans"].includes(m.kind)) {
        const ref = refs[payload.assetId ? "assets" : "customers"].find(
          (x) => x.id === Number(payload.assetId || payload.customerId),
        );
        if (ref) payload.orgId = ref.orgId;
      }
      const path =
        m.kind === "orders"
          ? "/orders"
          : (adminPage.value ? "/admin/" : "/catalog/") +
            m.kind +
            (m.id ? "/" + m.id : "");
      await request(path, { method: m.id ? "PUT" : "POST", body: payload });
    } else {
      const moves = {
        receive: "RECEIVE",
        issue: "ISSUE",
        consume: "CONSUME",
        return: "RETURN",
      };
      if (moves[m.action])
        await request("/inventory/movements", {
          method: "POST",
          body: { ...payload, kind: moves[m.action] },
        });
      else
        await request("/orders/" + m.id + "/" + m.action, {
          method: "POST",
          body: payload,
        });
    }
    modal.value = null;
    await loadRefs();
    if (detail.value && m.type === "command") {
      detail.value = await request("/orders/" + detail.value.order.id);
    } else await load();
    message.value = "操作已保存";
  });
}
async function viewOrder(row) {
  await run(async () => {
    detail.value = await request("/orders/" + row.id);
    advice.value = null;
  });
}
async function disable(row) {
  await run(async () => {
    await request(
      "/catalog/" + current.value + "/" + row.id + "?version=" + row.version,
      { method: "DELETE" },
    );
    await load();
    message.value = "记录已停用，历史保留";
  });
}
async function trigger() {
  await run(async () => {
    const result = await request("/plans/trigger", { method: "POST" });
    message.value = "已生成 " + result.generated + " 笔维保工单";
    await load();
    await loadRefs();
  });
}
async function importData() {
  await run(async () => {
    const result = await request("/catalog/" + current.value + "/import", {
      method: "POST",
      body: importRows(importText.value),
    });
    modal.value = null;
    importText.value = "";
    await load();
    await loadRefs();
    message.value = "已导入 " + result.imported + " 条";
  });
}
async function changePassword() {
  await run(async () => {
    await request("/auth/password", {
      method: "POST",
      body: { oldPassword: oldPassword.value, newPassword: newPassword.value },
    });
    oldPassword.value = "";
    newPassword.value = "";
    me.value = null;
    message.value = "密码已更新，请重新登录";
  });
}
async function upload(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  await run(async () => {
    const data = new FormData();
    data.append("file", file);
    await request("/orders/" + detail.value.order.id + "/attachments", {
      method: "POST",
      body: data,
    });
    detail.value = await request("/orders/" + detail.value.order.id);
    message.value = "附件已保存";
  });
  event.target.value = "";
}
async function searchKnowledge() {
  await run(async () => {
    advice.value = await request(
      "/orders/" +
        detail.value.order.id +
        "/advice?" +
        new URLSearchParams({ q: form.knowledgeQuery || "" }),
    );
  });
}
async function suggest() {
  await run(async () => {
    advice.value = await request(
      "/orders/" +
        detail.value.order.id +
        "/advice?" +
        new URLSearchParams({ q: advice.value?.query || "" }),
    );
  });
}
const actions = [
  {
    key: "quote",
    name: "填写报价",
    permission: "orders.dispatch",
    states: ["PENDING"],
  },
  {
    key: "approve-quote",
    name: "确认报价",
    permission: "orders.confirm",
    states: ["PENDING"],
  },
  {
    key: "dispatch",
    name: "指派工程师",
    permission: "orders.dispatch",
    states: ["PENDING", "DISPATCHED"],
  },
  {
    key: "accept",
    name: "接单处理",
    permission: "orders.execute",
    states: ["DISPATCHED"],
  },
  {
    key: "wait-parts",
    name: "等待备件",
    permission: "orders.execute",
    states: ["IN_PROGRESS"],
  },
  {
    key: "wait-customer",
    name: "等待客户",
    permission: "orders.execute",
    states: ["IN_PROGRESS"],
  },
  {
    key: "resume",
    name: "恢复处理",
    permission: "orders.execute",
    states: ["WAIT_PARTS", "WAIT_CUSTOMER"],
  },
  {
    key: "finish",
    name: "提交完工复核",
    permission: "orders.execute",
    states: ["IN_PROGRESS"],
  },
  {
    key: "approve",
    name: "复核通过",
    permission: "orders.review",
    states: ["REVIEW"],
  },
  {
    key: "reject",
    name: "退回重做",
    permission: "orders.review",
    states: ["REVIEW", "CONFIRM"],
  },
  {
    key: "confirm",
    name: "确认并关闭",
    permission: "orders.confirm",
    states: ["CONFIRM"],
  },
  {
    key: "settle",
    name: "登记结算",
    permission: "orders.close",
    states: ["CLOSED"],
  },
  {
    key: "cancel",
    name: "取消工单",
    permission: "orders.cancel",
    states: [
      "PENDING",
      "DISPATCHED",
      "IN_PROGRESS",
      "WAIT_PARTS",
      "WAIT_CUSTOMER",
    ],
  },
  {
    key: "reopen",
    name: "重新处理",
    permission: "orders.reopen",
    states: ["CLOSED"],
  },
];
const availableActions = computed(() =>
  detail.value
    ? actions.filter(
        (a) =>
          can(a.permission) &&
          a.states.includes(detail.value.order.state) &&
          (!["quote", "approve-quote"].includes(a.key) ||
            detail.value.order.coverage === "CHARGEABLE") &&
          (!["settle", "reopen"].includes(a.key) ||
            detail.value.order.settlementState !== "SETTLED"),
      )
    : [],
);
function remaining(issue) {
  return (
    issue.quantity -
    detail.value.movements
      .filter((m) => m.issueId === issue.id)
      .reduce((sum, m) => sum + m.quantity, 0)
  );
}
onMounted(async () => {
  try {
    await session();
  } catch (e) {
    if (e.status !== 401) error.value = e.message;
  } finally {
    initial.value = false;
  }
});
</script>

<template>
  <div v-if="initial" class="loading">正在连接服务…</div>
  <main v-else-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span>知华科技</span>
      <h1>企业服务运营平台</h1>
      <p>设备服务 · 现场交付 · 维保管理</p>
      <div class="brand-line"></div>
      <p class="edition">公开源码学习版 · 非商业源码版</p>
    </section>
    <form class="login-form" @submit.prevent="login">
      <p class="eyebrow">SERVICE OPERATIONS</p>
      <h2>登录工作台</h2>
      <p class="muted">使用管理员分配的账号登录。</p>
      <label
        >登录账号<input
          v-model="username"
          autocomplete="username"
          required
          autofocus /></label
      ><label
        >密码<input
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <p v-if="message" role="status" class="success">{{ message }}</p>
      <button class="primary" :disabled="busy">
        {{ busy ? "正在登录…" : "登录" }}
      </button>
      <footer>
        上海如静知华信息科技有限公司<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >知华科技官网</a
        >
        · 咨询微信 zhuatech / zhuatech2
      </footer>
    </form>
  </main>
  <div v-else class="shell">
    <aside>
      <div class="logo">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>知华 ServiceOps<small>企业服务运营平台</small></div>
      </div>
      <nav aria-label="主导航">
        <router-link
          v-for="item in navigation"
          :key="item[0]"
          :to="'/' + item[0]"
          :class="{ selected: current === item[0] }"
          ><span>{{ item[1] }}</span
          ><span v-if="current === item[0]">›</span></router-link
        >
      </nav>
      <div class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 ↗</a
        ><small>公开源码学习版 0.1.0</small>
      </div>
    </aside>
    <div class="workspace">
      <header>
        <div><span class="muted">服务运营 / </span>{{ title }}</div>
        <div class="user">
          {{ me.displayName
          }}<button class="quiet" :disabled="busy" @click="logout">退出</button>
        </div>
      </header>
      <main class="content">
        <div class="page-heading">
          <div>
            <p class="eyebrow">
              {{
                current === "dashboard"
                  ? "OPERATIONS OVERVIEW"
                  : "SERVICE WORKSPACE"
              }}
            </p>
            <h1>{{ detail ? "工单 #" + detail.order.id : title }}</h1>
          </div>
          <span class="edition-pill">公开源码学习版</span>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="message" role="status" class="success">{{ message }}</p>
        <section v-if="current === 'dashboard'" class="dashboard">
          <div v-if="stats" class="metrics">
            <article>
              <span>服务请求</span><strong>{{ stats.total }}</strong
              ><small>当前数据范围内</small>
            </article>
            <article>
              <span>处理中及待处理</span
              ><strong>{{
                stats.total - stats.closed - (stats.states.CANCELLED || 0)
              }}</strong
              ><small>包含待料与待确认</small>
            </article>
            <article>
              <span>超时未完成</span
              ><strong class="warning">{{ stats.overdue }}</strong
              ><small>待料、待客户暂停计时</small>
            </article>
            <article>
              <span>已关闭工单</span><strong>{{ stats.closed }}</strong
              ><small>已完成客户确认</small>
            </article>
          </div>
          <div class="dashboard-grid">
            <article class="panel">
              <h2>服务状态分布</h2>
              <div v-if="stats?.total" class="state-list">
                <div v-for="(count, key) in stats.states" :key="key">
                  <span>{{ label(key) }}</span>
                  <div class="bar">
                    <i
                      :style="{ width: (count / stats.total) * 100 + '%' }"
                    ></i>
                  </div>
                  <b>{{ count }}</b>
                </div>
              </div>
              <p v-else class="empty">
                暂无服务请求。客户与设备建档后即可创建工单。
              </p>
            </article>
            <article class="panel">
              <h2>常用操作</h2>
              <div class="shortcuts">
                <button
                  v-if="can('orders.create')"
                  @click="router.push('/orders')"
                >
                  创建与跟进服务请求 →</button
                ><button
                  v-if="can('catalog.write')"
                  @click="router.push('/assets')"
                >
                  维护设备台账 →</button
                ><button
                  v-if="can('plans.manage')"
                  @click="router.push('/plans')"
                >
                  查看维保计划 →</button
                ><button
                  v-if="can('inventory.write')"
                  @click="router.push('/parts')"
                >
                  备件入库与领用 →
                </button>
              </div>
            </article>
          </div>
          <article v-if="stats?.showFinance" class="panel finance">
            <div>
              <span>累计人工与备件成本</span
              ><strong>¥ {{ money(stats.cost) }}</strong>
            </div>
            <div>
              <span>已关闭收费工单报价</span
              ><strong>¥ {{ money(stats.charge) }}</strong>
            </div>
            <p>
              费用为业务记录统计，不代表实际到账。真实收款完成后登记结算凭证。
            </p>
          </article>
          <article class="panel">
            <div class="panel-heading">
              <h2>最近服务请求</h2>
              <router-link to="/orders">全部工单 →</router-link>
            </div>
            <div v-if="rows.length" class="recent">
              <button
                v-for="row in rows"
                :key="row.id"
                @click="router.push('/orders').then(() => viewOrder(row))"
              >
                <span
                  ><b>#{{ row.id }} {{ row.name }}</b
                  ><small
                    >{{ label(row.coverage) }} ·
                    {{ time(row.createdAt) }}</small
                  ></span
                ><span :class="['status', row.state]">{{
                  label(row.state)
                }}</span>
              </button>
            </div>
            <p v-else class="empty">暂无服务请求</p>
          </article>
        </section>
        <section v-else-if="current === 'account'" class="account-grid">
          <form class="panel" @submit.prevent="changePassword">
            <h2>修改个人密码</h2>
            <label
              >当前密码<input
                v-model="oldPassword"
                type="password"
                autocomplete="current-password"
                required /></label
            ><label
              >新密码<input
                v-model="newPassword"
                type="password"
                minlength="12"
                maxlength="128"
                autocomplete="new-password"
                required
            /></label>
            <p class="muted">至少12位。保存后需要重新登录。</p>
            <button class="primary" :disabled="busy">保存密码</button>
          </form>
          <article class="panel">
            <h2>关于平台</h2>
            <p>知华企业服务运营平台 0.1.0</p>
            <p>
              公开源码学习版，仅限个人学习、技术研究与非商业交流，商用须书面授权。
            </p>
            <p>上海如静知华信息科技有限公司</p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >www.zhuatech.cn</a
            >
            <p>商业授权、部署与定制咨询微信：zhuatech / zhuatech2</p>
          </article>
        </section>
        <section v-else-if="detail" class="order-detail">
          <div class="toolbar">
            <button
              @click="
                detail = null;
                run(load);
              "
            >
              ← 返回列表</button
            ><span :class="['status', detail.order.state]">{{
              label(detail.order.state)
            }}</span
            ><button
              v-for="action in availableActions"
              :key="action.key"
              :disabled="busy"
              class="primary"
              @click="openCommand(action.key, detail.order)"
            >
              {{ action.name }}
            </button>
          </div>
          <div class="detail-grid">
            <article class="panel">
              <h2>{{ detail.order.name }}</h2>
              <dl>
                <dt>设备</dt>
                <dd>
                  {{ detail.asset.name }} · {{ detail.asset.serialNumber }}
                </dd>
                <dt>服务权益</dt>
                <dd>{{ label(detail.order.coverage) }}</dd>
                <dt>完成时限</dt>
                <dd>{{ time(detail.order.dueAt) }}</dd>
                <dt>首次响应</dt>
                <dd>{{ time(detail.order.respondBy) }}</dd>
                <dt>服务要求</dt>
                <dd class="pre">{{ detail.order.description }}</dd>
                <dt>诊断</dt>
                <dd class="pre">{{ detail.order.diagnosis || "未记录" }}</dd>
                <dt>处理结果</dt>
                <dd class="pre">{{ detail.order.resolution || "未记录" }}</dd>
                <dt>等待或退回原因</dt>
                <dd>{{ detail.order.reason || "—" }}</dd>
                <dt>安全/功能检查</dt>
                <dd>
                  {{ detail.order.safetyChecked ? "已检查" : "未检查" }} /
                  {{ detail.order.testPassed ? "通过" : "未通过" }}
                </dd>
              </dl>
            </article>
            <article class="panel">
              <h2>费用与结算</h2>
              <dl>
                <dt>确认报价</dt>
                <dd>¥ {{ money(detail.charge) }}</dd>
                <dt>报价确认</dt>
                <dd>
                  {{ detail.order.quoteApproved ? "已确认" : "待客户确认" }}
                </dd>
                <template v-if="can('report.read')"
                  ><dt>人工与备件成本</dt>
                  <dd>¥ {{ money(detail.cost) }}</dd></template
                >
                <dt>结算状态</dt>
                <dd>{{ label(detail.order.settlementState) }}</dd>
                <dt>收款凭证号</dt>
                <dd>{{ detail.order.paymentReference || "未登记" }}</dd>
              </dl>
              <p class="muted">
                登记结算仅记录已经完成的收款，不发起在线支付。
              </p>
              <h3>服务附件</h3>
              <ul>
                <li v-for="file in detail.attachments" :key="file.id">
                  <a :href="'/api/attachments/' + file.id">{{
                    file.originalName
                  }}</a>
                </li>
              </ul>
              <label
                v-if="
                  can('orders.execute') &&
                  !['CLOSED', 'CANCELLED'].includes(detail.order.state)
                "
                class="upload"
                >上传图片或 PDF（最多5MB）<input
                  type="file"
                  accept="image/png,image/jpeg,application/pdf"
                  :disabled="busy"
                  @change="upload"
              /></label>
            </article>
          </div>
          <article class="panel">
            <div class="panel-heading"><h2>备件处理</h2></div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>流水</th>
                    <th>操作</th>
                    <th>备件</th>
                    <th>数量</th>
                    <th>领用余量</th>
                    <th>说明</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in detail.movements" :key="row.id">
                    <td>#{{ row.id }}</td>
                    <td>{{ label(row.kind) }}</td>
                    <td>#{{ row.partId }}</td>
                    <td>{{ row.quantity }}</td>
                    <td>{{ row.kind === "ISSUE" ? remaining(row) : "—" }}</td>
                    <td>{{ row.reason }}</td>
                    <td>
                      <template
                        v-if="row.kind === 'ISSUE' && remaining(row) > 0"
                        ><button
                          v-if="can('orders.execute')"
                          class="link"
                          @click="
                            openCommand('consume', detail.order, {
                              partId: row.partId,
                              orderId: detail.order.id,
                              issueId: row.id,
                            })
                          "
                        >
                          消耗</button
                        ><button
                          v-if="can('inventory.write')"
                          class="link"
                          @click="
                            openCommand('return', detail.order, {
                              partId: row.partId,
                              orderId: detail.order.id,
                              issueId: row.id,
                            })
                          "
                        >
                          退回
                        </button></template
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!detail.movements.length" class="empty">暂无备件流水</p>
            </div>
          </article>
          <article v-if="can('knowledge.read')" class="panel">
            <div class="panel-heading">
              <h2>处理建议</h2>
              <button :disabled="busy" @click="suggest">
                查看本地规则建议
              </button>
            </div>
            <p v-if="advice" class="muted">
              模式：本地规则。建议不会自动修改业务。
            </p>
            <ul v-if="advice">
              <li v-for="item in advice.checks" :key="item">{{ item }}</li>
            </ul>
            <form class="inline-form" @submit.prevent="searchKnowledge">
              <input
                v-model="form.knowledgeQuery"
                placeholder="搜索已发布的故障知识"
                aria-label="搜索故障知识"
              /><button :disabled="busy">检索</button>
            </form>
            <div
              v-for="source in advice?.sources || []"
              :key="source.id"
              class="knowledge-result"
            >
              <b>{{ source.name }} · 版本 {{ source.version }}</b>
              <p class="pre">{{ source.content }}</p>
            </div>
          </article>
        </section>
        <section v-else class="panel list-panel">
          <div class="toolbar">
            <form class="inline-form" @submit.prevent="search">
              <input
                v-model="q"
                :placeholder="'搜索' + title"
                aria-label="搜索"
              /><select
                v-if="current === 'orders'"
                v-model="state"
                aria-label="工单状态"
              >
                <option value="">全部状态</option>
                <option
                  v-for="key in [
                    'PENDING',
                    'DISPATCHED',
                    'IN_PROGRESS',
                    'WAIT_PARTS',
                    'WAIT_CUSTOMER',
                    'REVIEW',
                    'CONFIRM',
                    'CLOSED',
                    'CANCELLED',
                  ]"
                  :key="key"
                  :value="key"
                >
                  {{ label(key) }}
                </option></select
              ><select v-model="sort" aria-label="排序">
                <option value="newest">最新创建</option>
                <option value="oldest">最早创建</option>
                <option value="updated">最近更新</option></select
              ><button :disabled="busy">查询</button>
            </form>
            <div class="toolbar-actions">
              <button
                v-if="current === 'plans' && can('plans.manage')"
                :disabled="busy"
                @click="trigger"
              >
                生成到期工单</button
              ><a
                v-if="current === 'orders' && can('report.read')"
                class="button"
                :href="exportUrl"
                >导出 CSV</a
              ><button
                v-if="['customers', 'parts'].includes(current) && writable"
                @click="modal = { type: 'import' }"
              >
                批量导入</button
              ><button
                v-if="schemas[current] && writable"
                class="primary"
                @click="openEditor()"
              >
                {{ current === "orders" ? "新建服务请求" : "新增" }}
              </button>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>编号</th>
                  <th v-for="col in currentColumns" :key="col[0]">
                    {{ col[1] }}
                  </th>
                  <th v-if="schemas[current]">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td class="id">#{{ row.id }}</td>
                  <td v-for="col in currentColumns" :key="col[0]">
                    <span
                      v-if="col[0] === 'state'"
                      :class="['status', row.state]"
                      >{{ label(row.state) }}</span
                    ><span
                      v-else
                      :class="{ muted: col[0] === 'enabled' && !row.enabled }"
                      >{{ display(row, col[0]) }}</span
                    >
                  </td>
                  <td v-if="schemas[current]" class="row-actions">
                    <button
                      v-if="current === 'orders'"
                      class="link"
                      @click="viewOrder(row)"
                    >
                      查看处理</button
                    ><template v-else
                      ><button
                        v-if="writable && !(current === 'roles' && row.builtin)"
                        class="link"
                        @click="openEditor(row)"
                      >
                        编辑</button
                      ><button
                        v-if="writable && !adminPage && row.enabled"
                        class="link"
                        @click="disable(row)"
                      >
                        停用</button
                      ><template
                        v-if="current === 'parts' && can('inventory.write')"
                        ><button
                          class="link"
                          @click="
                            openCommand('receive', row, { partId: row.id })
                          "
                        >
                          入库</button
                        ><button
                          class="link"
                          @click="openCommand('issue', row, { partId: row.id })"
                        >
                          领用
                        </button></template
                      ></template
                    >
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ q ? "没有匹配的记录" : "暂无记录"
              }}<span v-if="schemas[current] && writable"
                >，可通过右上角新增。</span
              >
            </p>
          </div>
          <div class="pagination">
            <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
            <div>
              <button :disabled="busy || page === 0" @click="paginate(-1)">
                上一页</button
              ><button
                :disabled="busy || (page + 1) * 20 >= total"
                @click="paginate(1)"
              >
                下一页
              </button>
            </div>
          </div>
        </section>
      </main>
      <footer class="workspace-footer">
        上海如静知华信息科技有限公司 · 商业授权与部署咨询微信 zhuatech /
        zhuatech2
      </footer>
    </div>
  </div>
  <div v-if="modal && me" class="overlay" @click.self="!busy && (modal = null)">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        modal.type === 'import' ? '批量导入' : modal.name || '维护资料'
      "
    >
      <div class="panel-heading">
        <h2>
          {{
            modal.type === "import"
              ? "批量导入"
              : modal.name || (modal.id ? "编辑资料" : "新增资料")
          }}
        </h2>
        <button
          class="quiet"
          aria-label="关闭"
          :disabled="busy"
          @click="modal = null"
        >
          ×
        </button>
      </div>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <form v-if="modal.type === 'import'" @submit.prevent="importData">
        <p>
          输入 JSON 数组，每次最多100条，失败时全部回滚。字段说明见操作手册。
        </p>
        <label
          >导入数据<textarea
            v-model="importText"
            rows="10"
            required
          ></textarea></label
        ><button class="primary" :disabled="busy">校验并导入</button>
      </form>
      <form v-else @submit.prevent="submit">
        <p v-if="modal.type === 'command' && !fields.length">
          确认执行“{{ modal.name }}”？服务器将校验当前状态与版本。
        </p>
        <div class="form-grid">
          <template v-for="field in fields" :key="field.key"
            ><label v-if="field.type === 'checkbox'" class="check"
              ><input v-model="form[field.key]" type="checkbox" />{{
                field.label
              }}</label
            >
            <fieldset
              v-else-if="field.type === 'permissions'"
              class="permission-grid"
            >
              <legend>{{ field.label }}</legend>
              <label
                v-for="(name, key) in permissionNames"
                :key="key"
                class="check"
                ><input
                  type="checkbox"
                  :checked="(form.permissions || '').split(',').includes(key)"
                  @change="
                    (event) => {
                      const list = (form.permissions || '')
                        .split(',')
                        .filter(Boolean);
                      form.permissions = (
                        event.target.checked
                          ? [...list, key]
                          : list.filter((v) => v !== key)
                      ).join(',');
                    }
                  "
                />{{ name }}</label
              >
            </fieldset>
            <label v-else :class="{ wide: field.type === 'textarea' }"
              >{{ field.label
              }}<textarea
                v-if="field.type === 'textarea'"
                v-model="form[field.key]"
                rows="4"
                :required="field.required"
                maxlength="2000"
              ></textarea
              ><select
                v-else-if="field.type === 'priority'"
                v-model="form[field.key]"
              >
                <option value="NORMAL">普通</option>
                <option value="URGENT">紧急</option></select
              ><template v-else-if="refs[field.type]"
                ><input
                  v-model="form[field.key]"
                  type="number"
                  :list="'options-' + field.type"
                  min="1"
                  step="1"
                  :required="field.required"
                  :aria-label="field.label"
                /><datalist :id="'options-' + field.type">
                  <option
                    v-for="item in refs[field.type]"
                    :key="item.id"
                    :value="item.id"
                  >
                    {{ item.name || item.displayName }} (#{{ item.id }})
                  </option></datalist
                ><small class="muted">选择或输入有效编号</small></template
              ><input
                v-else
                v-model="form[field.key]"
                :type="field.type"
                :required="field.required"
                :step="field.type === 'number' ? 'any' : undefined"
                :min="field.type === 'number' ? 0 : undefined"
                :maxlength="field.type === 'password' ? 128 : 200" /></label
          ></template>
        </div>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="modal = null">
            取消</button
          ><button class="primary" :disabled="busy">
            {{ busy ? "正在保存…" : "保存" }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
