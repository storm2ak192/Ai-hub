#!/usr/bin/env python3
"""Restore AppStrings from known-good commit and add link-chooser strings."""
from pathlib import Path
import urllib.request

url = (
    "https://raw.githubusercontent.com/storm2ak192/Ai-hub/"
    "39bd965759fdda5fda8008b80c47bf95d9e8f94d/"
    "app/src/main/java/com/example/ui/localization/AppStrings.kt"
)
# If local file is already good (not PLACEHOLDER), use it; else download
path = Path("app/src/main/java/com/example/ui/localization/AppStrings.kt")
text = path.read_text(encoding="utf-8") if path.exists() else ""
if len(text) < 500 or "PLACEHOLDER" in text or "data class AppStrings" not in text:
    print("Restoring AppStrings from 39bd965...")
    # Prefer the pre-corruption blob on this branch history: cd3f3344 content via raw of parent
    # Use the commit before PLACEHOLDER: raw from 39bd965 path may already be broken.
    # Fall back to embedding download from git history is hard; try known path on main-ish:
    for candidate in [
        "https://raw.githubusercontent.com/storm2ak192/Ai-hub/39bd965759fdda5fda8008b80c47bf95d9e8f94d/app/src/main/java/com/example/ui/localization/AppStrings.kt",
        "https://raw.githubusercontent.com/storm2ak192/Ai-hub/ad76c4f077c6cba87b6cfff4c4329a675de01fbe/app/src/main/java/com/example/ui/localization/AppStrings.kt",
        "https://raw.githubusercontent.com/storm2ak192/Ai-hub/c50019ac166993f2b3c1e36d43ec170481fb1d42/app/src/main/java/com/example/ui/localization/AppStrings.kt",
    ]:
        try:
            req = urllib.request.Request(candidate, headers={"User-Agent": "GrokCI"})
            data = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")
            if "data class AppStrings" in data and len(data) > 500:
                text = data
                print("Restored from", candidate.split("/")[6][:8] if False else candidate)
                break
        except Exception as e:
            print("fail", e)
    else:
        raise SystemExit("Could not restore AppStrings.kt")

if "browserOpenInSecurePopup" not in text:
    text = text.replace(
        "    val browserOpenOnceIsolated: String,\n    val browserCancel: String,",
        "    val browserOpenOnceIsolated: String,\n"
        "    val browserCancel: String,\n"
        "    val browserOpenLinkTitle: String,\n"
        "    val browserOpenLinkDesc: String,\n"
        "    val browserOpenInSecurePopup: String,\n"
        "    val browserOpenInExternalBrowser: String,",
        1,
    )
    text = text.replace(
        '    browserOpenOnceIsolated = "فتح في جلسة مؤقتة معزولة",\n    browserCancel = "إلغاء",',
        '    browserOpenOnceIsolated = "فتح في جلسة مؤقتة معزولة",\n'
        '    browserCancel = "إلغاء",\n'
        '    browserOpenLinkTitle = "فتح الرابط",\n'
        '    browserOpenLinkDesc = "كيف تريد فتح هذا الرابط؟",\n'
        '    browserOpenInSecurePopup = "فتح في نافذة منبثقة آمنة",\n'
        '    browserOpenInExternalBrowser = "فتح في متصفح خارجي",',
        1,
    )
    text = text.replace(
        '    browserOpenOnceIsolated = "Open Once (Isolated)",\n    browserCancel = "Cancel",',
        '    browserOpenOnceIsolated = "Open Once (Isolated)",\n'
        '    browserCancel = "Cancel",\n'
        '    browserOpenLinkTitle = "Open Link",\n'
        '    browserOpenLinkDesc = "How do you want to open this link?",\n'
        '    browserOpenInSecurePopup = "Open in Secure Popup",\n'
        '    browserOpenInExternalBrowser = "Open in External Browser",',
        1,
    )
    print("OK: link-chooser strings added")
else:
    print("OK: strings already present")

path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(text, encoding="utf-8")
print("AppStrings length", len(text))
