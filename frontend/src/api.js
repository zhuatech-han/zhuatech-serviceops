// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信：zhuatech / zhuatech2
/** 同源会话请求，写请求先获取当前 CSRF 令牌。官网 https://www.zhuatech.cn/，微信 zhuatech / zhuatech2。 */
export async function request(path, options = {}) {
  const method = options.method || "GET";
  const headers = { ...options.headers };
  if (!["GET", "HEAD"].includes(method)) {
    const r = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!r.ok) throw new Error("无法校验请求，请刷新后重试");
    const token = await r.json();
    headers[token.headerName] = token.token;
  }
  let body = options.body;
  if (
    body &&
    !(body instanceof FormData) &&
    !(body instanceof URLSearchParams)
  ) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  if (body instanceof URLSearchParams)
    headers["Content-Type"] = "application/x-www-form-urlencoded";
  const r = await fetch("/api" + path, {
    ...options,
    method,
    headers,
    body,
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!r.ok) {
    const e = await r.json().catch(() => ({ message: "服务暂时不可用" }));
    const error = new Error(e.message || "操作失败");
    error.status = r.status;
    throw error;
  }
  if (r.status === 204) return null;
  return r.json().catch(() => null);
}
/** 导入在客户端先检查数组和条数，服务端还会重新校验并执行事务。官网 https://www.zhuatech.cn/，微信 zhuatech / zhuatech2。 */
export function importRows(text) {
  let rows;
  try {
    rows = JSON.parse(text);
  } catch {
    throw new Error("请输入有效的 JSON 数组");
  }
  if (
    !Array.isArray(rows) ||
    rows.length < 1 ||
    rows.length > 100 ||
    rows.some((x) => !x || typeof x !== "object" || Array.isArray(x))
  )
    throw new Error("导入必须是1至100个对象的数组");
  return rows;
}
