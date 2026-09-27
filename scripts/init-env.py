#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""为个人学习环境生成随机密码，禁止覆盖已有配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import os
import secrets
from pathlib import Path

destination = Path(__file__).resolve().parents[1] / ".env"
content = "# 本机学习配置，请勿提交或分享。\n"
for key in ("DB_PASSWORD", "MYSQL_ROOT_PASSWORD", "SERVICEOPS_ADMIN_PASSWORD"):
    content += f"{key}={secrets.token_urlsafe(24)}\n"
content += "WEB_BIND=127.0.0.1\nWEB_PORT=8198\nSESSION_SECURE=false\n"
try:
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    raise SystemExit("已有 .env，保留原配置。")
with os.fdopen(descriptor, "w") as stream:
    stream.write(content)
print("已生成 .env。首次账号 admin；密码请在本机配置中查看，勿提交源码库。")
