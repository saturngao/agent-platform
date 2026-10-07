# 示例脚本：入参从同目录 input.json 读取，也可读环境变量 SKILL_PARAMS
import json, os

raw = os.environ.get("SKILL_PARAMS") or "{}"
try:
    with open("input.json", "r", encoding="utf-8") as f:
        raw = f.read()
except Exception:
    pass

try:
    args = json.loads(raw)
except Exception:
    args = {}

rows = float(args.get("table_rows", 0) or 0)
sel = float(args.get("selectivity", 1) or 1)
has_index = bool(args.get("has_index", False))

scanned = rows if not has_index else max(1.0, rows * sel)
cost = round(scanned / 10000.0, 2)

print(json.dumps({
    "table_rows": rows,
    "has_index": has_index,
    "estimated_scanned_rows": int(scanned),
    "estimated_cost": cost,
    "advice": "命中索引，扫描量可控" if has_index and scanned < rows * 0.1 else "建议补充或调整索引"
}, ensure_ascii=False))
