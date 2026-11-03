#!/usr/bin/env python3
"""Post-process patched BrowserScreen.kt for compile safety."""
from pathlib import Path

p = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
t = p.read_text(encoding="utf-8")

t = t.replace("view?.loadUrl(u)", "mainWv.loadUrl(u)")

CHROME_UA = (
    '"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"'
)
t = t.replace("CHROME_MOBILE_USER_AGENT", CHROME_UA)

cleaned = []
for line in t.splitlines(keepends=True):
    stripped = line.strip()
    if stripped.startswith("private val ") and "Mozilla/5.0" in stripped and "DESKTOP_USER_AGENT" not in stripped:
        print("drop bad line:", stripped[:80])
        continue
    cleaned.append(line)
t = "".join(cleaned)

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
print("has undefined const", "CHROME_MOBILE_USER_AGENT" in t)
print("has chrome ua", "Pixel 8" in t)
