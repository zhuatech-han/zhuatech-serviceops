#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""对独立学习测试实例执行真实 HTTP 业务验收；会写入明确标注的测试资料。
官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
"""
import argparse
import base64
import concurrent.futures
import http.cookiejar
import json
import os
import secrets
import unittest
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--url", default="http://127.0.0.1:8198")
parser.add_argument("--allow-test-data", action="store_true")
parser.add_argument("--receipt", type=Path)
args = parser.parse_args()
if not args.allow_test_data or urllib.parse.urlparse(args.url).hostname not in ("127.0.0.1", "localhost"):
    raise SystemExit("仅允许独立本机测试实例，须明确传入 --allow-test-data。")
config = {}
for line in (Path(__file__).resolve().parents[1] / ".env").read_text().splitlines():
    if line and not line.startswith("#") and "=" in line:
        key, value = line.split("=", 1)
        config[key] = value


class Client:
    """隔离每个账号的会话，写请求始终重新获取 CSRF。"""
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, path, body=None, method="GET", expected=200, raw=False, csrf=True, headers=None):
        h = dict(headers or {})
        if method not in ("GET", "HEAD") and csrf:
            token = self.call("/api/auth/csrf")
            h[token["headerName"]] = token["token"]
        if body is not None:
            if isinstance(body, bytes):
                data = body
            else:
                data = json.dumps(body).encode()
                h["Content-Type"] = "application/json"
        else:
            data = None
        request = urllib.request.Request(args.url + path, data=data, headers=h, method=method)
        try:
            response = self.opener.open(request, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read()
        if response.code != expected:
            raise AssertionError(f"{method} {path} HTTP {response.code}，预期 {expected}：{content[:240].decode(errors='replace')}")
        if raw:
            return content
        return json.loads(content) if content else None

    def login(self, username, password, expected=200):
        return self.call("/api/auth/login", urllib.parse.urlencode({"username": username, "password": password}).encode(), "POST", expected, headers={"Content-Type": "application/x-www-form-urlencoded"})


class Smoke(unittest.TestCase):
    """验收基于真实迁移、会话、权限和库存事务；不调用任何付费模型。"""
    @classmethod
    def setUpClass(cls):
        cls.prefix = "验收-" + secrets.token_hex(3)
        cls.password = secrets.token_urlsafe(20)
        cls.admin = Client()
        cls.admin.login("admin", config["SERVICEOPS_ADMIN_PASSWORD"])
        data = cls.admin.call("/api/admin")
        cls.org = data["organizations"][0]["id"]
        cls.roles = {r["name"]: r["id"] for r in data["roles"]}
        cls.customer = cls.create("customers", {"name": cls.prefix + "客户", "site": "测试工作区", "contact": "仅验收资料"})
        cls.asset = cls.create("assets", {"name": cls.prefix + "设备", "customerId": cls.customer["id"], "serialNumber": cls.prefix, "location": "验收设备区", "warrantyUntil": (date.today() + timedelta(days=365)).isoformat()})
        cls.engineer_name = "eng-" + secrets.token_hex(3)
        cls.customer_name = "cli-" + secrets.token_hex(3)
        cls.warehouse_name = "wh-" + secrets.token_hex(3)
        cls.engineer_account = cls.make_user(cls.engineer_name, "工程师")
        cls.make_user(cls.customer_name, "客户", cls.customer["id"])
        cls.make_user(cls.warehouse_name, "仓库")
        cls.engineer = Client(); cls.engineer.login(cls.engineer_name, cls.password)
        cls.client = Client(); cls.client.login(cls.customer_name, cls.password)
        cls.warehouse = Client(); cls.warehouse.login(cls.warehouse_name, cls.password)
        cls.closed_id = None

    @classmethod
    def create(cls, kind, body):
        return cls.admin.call("/api/catalog/" + kind, body, "POST")

    @classmethod
    def make_user(cls, name, role, customer=None):
        body = {"username": name, "displayName": cls.prefix + role, "password": cls.password, "orgId": cls.org, "roleId": cls.roles[role], "enabled": True}
        if customer: body["customerId"] = customer
        return cls.admin.call("/api/admin/users", body, "POST")

    def order(self, asset=None, title="服务工单"):
        return self.admin.call("/api/orders", {"name": self.prefix + title, "assetId": (asset or self.asset)["id"], "description": "仅用于学习版验收：记录设备检查过程", "priority": "NORMAL"}, "POST")

    def action(self, order, action, client=None, expected=200, **fields):
        return (client or self.admin).call(f'/api/orders/{order["id"]}/{action}', {"version": order["version"], **fields}, "POST", expected)

    def progressing(self):
        o = self.order()
        o = self.action(o, "dispatch", assigneeId=self.engineer_account["id"])
        return self.action(o, "accept", self.engineer)

    def part(self):
        return self.create("parts", {"name": self.prefix + "备件", "sku": secrets.token_hex(8), "unit": "件", "unitCost": "25.50"})

    def move(self, part, kind, quantity, order=None, issue=None, key=None, client=None, expected=200):
        body = {"partId": part["id"], "kind": kind, "quantity": quantity, "requestKey": key or secrets.token_hex(12), "reason": "独立测试实例库存验收"}
        if order: body["orderId"] = order["id"]
        if issue: body["issueId"] = issue["id"]
        return (client or self.admin).call("/api/inventory/movements", body, "POST", expected)

    def test_01_health_and_frontend(self):
        self.assertEqual("UP", Client().call("/health")["status"])
        self.assertIn(b'<div id="app">', Client().call("/", raw=True))
        self.assertIn(b'<div id="app">', Client().call("/orders", raw=True))
        self.assertIn(b'<div id="app">', Client().call("/assets", raw=True))

    def test_02_anonymous_and_wrong_password(self):
        Client().call("/api/admin", expected=401)
        Client().login("admin", "intentionally-wrong", expected=401)

    def test_03_csrf_blocks_unvalidated_write(self):
        self.admin.call("/api/catalog/customers", {"name": "应被拒绝"}, "POST", 403, csrf=False)

    def test_04_roles_protected_and_no_password_hash(self):
        self.engineer.call("/api/admin", expected=403)
        self.client.call("/api/catalog/parts", expected=403)
        data = self.admin.call("/api/admin")
        self.assertNotIn("passwordHash", json.dumps(data))
        self.assertNotIn("passwordHash", json.dumps(self.client.call("/api/auth/me")))

    def test_05_full_workflow_with_inventory_and_settlement(self):
        o = self.progressing(); p = self.part()
        self.move(p, "RECEIVE", 10, client=self.warehouse)
        issue = self.move(p, "ISSUE", 3, o, client=self.warehouse)
        self.action(o, "finish", self.engineer, expected=409, diagnosis="故障诊断", resolution="检查通过", laborMinutes=60, safetyChecked=True, testPassed=True)
        self.move(p, "CONSUME", 2, o, issue, client=self.engineer)
        self.move(p, "RETURN", 1, o, issue, client=self.warehouse)
        o = self.admin.call(f'/api/orders/{o["id"]}')["order"]
        o = self.action(o, "finish", self.engineer, diagnosis="耗材检查", resolution="更换耗材并完成安全与功能检查", laborMinutes=60, safetyChecked=True, testPassed=True)
        self.move(p, "ISSUE", 1, o, expected=409)
        o = self.action(o, "approve")
        public = self.client.call(f'/api/orders/{o["id"]}')
        self.assertNotIn("partCost", public["order"]); self.assertNotIn("laborRate", public["order"])
        self.assertTrue(all("unitCost" not in m for m in public["movements"]))
        self.assertNotIn("partCost", json.dumps(self.client.call("/api/catalog/orders")))
        o = self.action(o, "confirm", self.client)
        o = self.action(o, "settle", paymentReference="TEST-ZERO-CHARGE-REGISTER")
        self.assertEqual("CLOSED", o["state"]); self.assertEqual("SETTLED", o["settlementState"])
        self.assertEqual(171, self.admin.call(f'/api/orders/{o["id"]}')["cost"])
        self.action(o, "reopen", expected=409, reason="禁止重开已结算账单")
        type(self).closed_id = o["id"]

    def test_06_chargeable_quote_gate(self):
        a = self.create("assets", {"name": self.prefix + "非保内设备", "serialNumber": secrets.token_hex(8), "customerId": self.customer["id"]})
        o = self.order(a)
        self.action(o, "dispatch", expected=409, assigneeId=self.engineer_account["id"])
        o = self.action(o, "quote", quotedAmount="300.00", reason="收费服务报价")
        o = self.action(o, "approve-quote", self.client)
        o = self.action(o, "dispatch", assigneeId=self.engineer_account["id"])
        self.assertEqual("DISPATCHED", o["state"])

    def test_07_idempotency_and_stock_shortage(self):
        p = self.part(); key = secrets.token_hex(12)
        a = self.move(p, "RECEIVE", 5, key=key); b = self.move(p, "RECEIVE", 5, key=key)
        self.assertEqual(a["id"], b["id"])
        self.move(p, "RECEIVE", 6, key=key, expected=409)
        o = self.progressing(); self.move(p, "ISSUE", 6, o, expected=409)
        stored = next(x for x in self.admin.call("/api/catalog/parts?size=100")["items"] if x["id"] == p["id"])
        self.assertEqual(5, stored["stock"])

    def test_08_concurrent_issue_cannot_oversell(self):
        p = self.part(); self.move(p, "RECEIVE", 1)
        orders = [self.progressing(), self.progressing()]
        def issue(o):
            c = Client(); c.login("admin", config["SERVICEOPS_ADMIN_PASSWORD"])
            try: self.move(p, "ISSUE", 1, o, client=c); return 200
            except AssertionError as error:
                if "HTTP 409" in str(error): return 409
                raise
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual([200, 409], sorted(pool.map(issue, orders)))

    def test_09_customer_and_engineer_scope(self):
        c = self.create("customers", {"name": self.prefix + "其他客户"})
        a = self.create("assets", {"name": self.prefix + "其他设备", "serialNumber": secrets.token_hex(8), "customerId": c["id"]})
        o = self.order(a)
        self.client.call(f'/api/orders/{o["id"]}', expected=404)
        self.engineer.call(f'/api/orders/{o["id"]}', expected=404)
        self.assertTrue(all(x["customerId"] == self.customer["id"] for x in self.client.call("/api/catalog/orders?size=100")["items"]))

    def test_10_plan_generation_is_repeatable(self):
        plan = self.create("plans", {"name": self.prefix + "周期维保", "assetId": self.asset["id"], "nextDate": date.today().isoformat(), "intervalDays": 30, "description": "检查运行状态和安全防护"})
        self.admin.call("/api/plans/trigger", {}, "POST")
        before = self.admin.call("/api/catalog/orders?size=100")["items"]
        self.admin.call("/api/plans/trigger", {}, "POST")
        after = self.admin.call("/api/catalog/orders?size=100")["items"]
        self.assertEqual(1, sum(x["planId"] == plan["id"] for x in before))
        self.assertEqual(1, sum(x["planId"] == plan["id"] for x in after))

    def test_11_import_rolls_back(self):
        name = self.prefix + "应回滚"
        self.admin.call("/api/catalog/customers/import", [{"name": name}, {"name": ""}], "POST", 400)
        self.assertEqual(0, self.admin.call("/api/catalog/customers?q=" + urllib.parse.quote(name))["total"])

    def test_12_version_state_and_csv_export(self):
        o = self.order()
        self.admin.call(f'/api/orders/{o["id"]}/cancel', {"version": 9999, "reason": "测试"}, "POST", 409)
        self.action(o, "confirm", expected=409)
        self.assertTrue(self.admin.call("/api/reports/orders.csv", raw=True).startswith(b"\xef\xbb\xbf"))
        self.client.call("/api/reports/orders.csv", expected=403)

    def test_13_wait_resume_and_review_return(self):
        o = self.progressing()
        o = self.action(o, "wait-parts", self.engineer, reason="等待备件到货")
        old_due = o["dueAt"]
        o = self.action(o, "resume", self.engineer)
        self.assertGreaterEqual(o["dueAt"], old_due)
        o = self.action(o, "finish", self.engineer, diagnosis="检查", resolution="完成测试", laborMinutes=15, safetyChecked=True, testPassed=True)
        o = self.action(o, "reject", reason="补充验证记录")
        self.assertEqual("IN_PROGRESS", o["state"]); self.assertFalse(o["testPassed"])

    def test_14_local_advice_has_real_sources(self):
        k = self.create("knowledge", {"name": self.prefix + "故障检查", "category": "维护", "content": "仅验收资料：检查运行状态与备件记录", "published": True})
        o = self.progressing()
        advice = self.engineer.call(f'/api/orders/{o["id"]}/advice?q=' + urllib.parse.quote(self.prefix))
        self.assertEqual("LOCAL_RULES", advice["mode"]); self.assertFalse(advice["changesBusinessData"])
        self.assertTrue(any(x["id"] == k["id"] for x in advice["sources"]))

    def test_15_password_change_rejects_other_sessions(self):
        name = "pwd-" + secrets.token_hex(3); self.make_user(name, "工程师")
        first = Client(); second = Client(); first.login(name, self.password); second.login(name, self.password)
        next_password = secrets.token_urlsafe(24)
        first.call("/api/auth/password", {"oldPassword": self.password, "newPassword": next_password}, "POST")
        second.call("/api/auth/me", expected=401)
        first.call("/api/auth/me", expected=401)
        Client().login(name, next_password)

    def test_16_attachment_upload_download_and_scope(self):
        o = self.progressing()
        png=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jfuoAAAAASUVORK5CYII=")
        boundary="ServiceOpsTestBoundary"+secrets.token_hex(8)
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="serviceops-test.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
        attachment=self.engineer.call(f'/api/orders/{o["id"]}/attachments',body,"POST",headers={"Content-Type":"multipart/form-data; boundary="+boundary})
        self.assertNotIn("storageName", attachment)
        self.assertEqual(png,self.client.call(f'/api/attachments/{attachment["id"]}',raw=True))
        c=self.create("customers",{"name":self.prefix+"附件隔离客户"})
        name="file-"+secrets.token_hex(3);self.make_user(name,"客户",c["id"])
        other=Client();other.login(name,self.password);other.call(f'/api/attachments/{attachment["id"]}',expected=404)
        Client().call(f'/api/attachments/{attachment["id"]}',expected=401)

    @classmethod
    def tearDownClass(cls):
        if args.receipt:
            result = {"prefix": cls.prefix, "customerUsername": cls.customer_name, "engineerUsername": cls.engineer_name, "testPassword": cls.password, "customerId": cls.customer["id"], "closedOrderId": cls.closed_id}
            descriptor = os.open(args.receipt, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(descriptor, "w") as stream: json.dump(result, stream)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(Smoke)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
