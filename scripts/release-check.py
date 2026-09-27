#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核查当前自有源码、说明与常见敏感模式；不替代人工安全审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
errors = []
readme = (root / "README.md").read_text()
for relative in re.findall(r'!\[[^\]]*\]\(([^)]+)\)|<img[^>]+src="([^"]+)"', readme):
    target = next(x for x in relative if x)
    if not (root / target).is_file():
        errors.append("README图片缺失：" + target)
for target in ("frontend/public/brand/logo.jpg", "frontend/public/brand/wechat-1.png", "frontend/public/brand/wechat-2.png"):
    if not (root / target).is_file(): errors.append("品牌素材缺失：" + target)
for text in ("上海如静知华信息科技有限公司", "https://www.zhuatech.cn/", "zhuatech2", "未经书面授权不得商用"):
    if text not in readme: errors.append("README品牌或授权缺失：" + text)
license_text = (root / "LICENSE").read_text()
if "非商业" not in license_text or "上海如静知华信息科技有限公司" not in license_text:
    errors.append("LICENSE不完整")
files = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root, text=True).split("\0")[:-1]
patterns = [r"-----BEGIN (?:OPENSSH |RSA |EC )?PRIVATE KEY-----", r"gh[pousr]_[A-Za-z0-9]{25,}", r"github_pat_[A-Za-z0-9_]{30,}", r"sk-[A-Za-z0-9]{32,}", r"AKIA[A-Z0-9]{16}"]
for name in files:
    path = root / name
    if name == ".env" or name.startswith("backups/"): errors.append("敏感文件未被忽略：" + name)
    if path.suffix in (".png", ".jpg", ".jpeg"): continue
    content = path.read_text(errors="replace")
    for pattern in patterns:
        if re.search(pattern, content): errors.append("疑似凭证，仅报告路径：" + name)
    if path.suffix in (".java", ".js", ".vue", ".css", ".py", ".sql") and "zhuatech2" not in content[:600]:
        errors.append("自有源码文件头缺少联系入口：" + name)
if errors:
    raise SystemExit("\n".join(errors))
print(f"发布文件核查通过：{len(files)}个文件；README图片、品牌、LICENSE及常见敏感模式已检查。")
