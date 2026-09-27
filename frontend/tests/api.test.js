// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { request, importRows } from "../src/api.js";
test("批量导入限制对象数组和条数", () => {
  assert.deepEqual(importRows('[{"name":"测试客户"}]'), [{ name: "测试客户" }]);
  for (const s of [
    "{}",
    "[]",
    "[null]",
    "[[1]]",
    "bad",
    JSON.stringify(Array(101).fill({})),
  ])
    assert.throws(() => importRows(s));
});
test("写请求带当前CSRF令牌和同源会话", async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return {
      ok: true,
      status: 200,
      json: async () =>
        url.endsWith("/csrf")
          ? { token: "token-test", headerName: "X-CSRF-TOKEN" }
          : { id: 1 },
    };
  };
  assert.deepEqual(
    await request("/orders", { method: "POST", body: { name: "test" } }),
    { id: 1 },
  );
  assert.equal(calls[0].url, "/api/auth/csrf");
  assert.equal(calls[1].options.headers["X-CSRF-TOKEN"], "token-test");
  assert.equal(calls[1].options.credentials, "same-origin");
  assert.equal(calls[1].options.body, '{"name":"test"}');
});
test("认证错误保留状态供界面退出过期会话", async () => {
  globalThis.fetch = async () => ({
    ok: false,
    status: 401,
    json: async () => ({ message: "请登录" }),
  });
  await assert.rejects(
    request("/auth/me"),
    (e) => e.status === 401 && e.message === "请登录",
  );
});
test("CSRF获取失败时不提交业务", async () => {
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return { ok: false };
  };
  await assert.rejects(request("/orders", { method: "POST", body: {} }));
  assert.equal(count, 1);
});
