#!/usr/bin/env python3
"""Force Manage Sites reorder to up/down arrows only. No drag."""
from pathlib import Path
import re

path = Path("app/src/main/java/com/example/ui/screens/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

# Already arrows-only?
if (
    "move_up_" in text
    and "Icons.Filled.DragHandle" not in text
    and "detectDragGesturesAfterLongPress(" not in text
    and "draggingSiteId" not in text
):
    print("OK: arrows-only already")
    raise SystemExit(0)

# Remove all drag-related state
text = text.replace(
    "    var draggingIndex by remember { mutableStateOf<Int?>(null) }\n",
    "",
)
text = text.replace(
    "    var draggingSiteId by remember { mutableStateOf<String?>(null) }\n",
    "",
)
text = re.sub(
    r"    var dragOffsetY by remember \{ mutableFloatStateOf\(0f\) \}\n",
    "",
    text,
)

# Strip smooth full-card drag Card opener back to simple surface card
pat_smooth = re.compile(
    r"                Card\(\n"
    r"                    shape = MaterialTheme\.shapes\.medium,\n"
    r"                    colors = CardDefaults\.cardColors\([\s\S]*?\n"
    r"                    \),\n"
    r"                    modifier = Modifier\n"
    r"                        \.fillMaxWidth\(\)\n"
    r"                        \.graphicsLayer \{[\s\S]*?\n"
    r"                        \}\n"
    r"                        \.pointerInput\([\s\S]*?\n"
    r"                        \}\n"
    r"                \) \{",
    re.M,
)
text2, n = pat_smooth.subn(
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n"
    "                    modifier = Modifier.fillMaxWidth()\n"
    "                ) {",
    text,
    count=1,
)
if n:
    text = text2
    print("OK: stripped smooth drag card")

# Strip old graphicsLayer-only drag card
pat_old = re.compile(
    r"                    modifier = Modifier\n"
    r"                        \.fillMaxWidth\(\)\n"
    r"                        \.graphicsLayer \{[\s\S]*?\n"
    r"                        \}\n"
    r"                \) \{",
    re.M,
)
text2, n = pat_old.subn(
    "                    modifier = Modifier.fillMaxWidth()\n"
    "                ) {",
    text,
    count=2,
)
if n:
    text = text2
    print(f"OK: stripped graphicsLayer cards ({n})")

# DragHandle -> arrows
if "Icons.Filled.DragHandle" in text:
    start = text.find("imageVector = Icons.Filled.DragHandle")
    block_start = text.rfind("Icon(", 0, start)
    line_start = text.rfind("\n", 0, block_start) + 1
    clear_pos = text.find("onClick = { onClearSiteSession(site) }", start)
    btn_start = text.rfind("IconButton(", 0, clear_pos)
    line_btn = text.rfind("\n", 0, btn_start) + 1
    arrows = (
        "                            IconButton(\n"
        "                                onClick = { onMoveOrder(site.id, true) },\n"
        "                                enabled = index > 0,\n"
        '                                modifier = Modifier.testTag("move_up_${site.id}")\n'
        "                            ) {\n"
        "                                Icon(Icons.Filled.ArrowUpward, contentDescription = strings.moveUp)\n"
        "                            }\n"
        "                            IconButton(\n"
        "                                onClick = { onMoveOrder(site.id, false) },\n"
        "                                enabled = index < sites.lastIndex,\n"
        '                                modifier = Modifier.testTag("move_down_${site.id}")\n'
        "                            ) {\n"
        "                                Icon(Icons.Filled.ArrowDownward, contentDescription = strings.moveDown)\n"
        "                            }\n"
    )
    text = text[:line_start] + arrows + text[line_btn:]
    print("OK: DragHandle -> arrows")

path.write_text(text, encoding="utf-8")
print("has arrows", "move_up_" in text)
print("has drag body", "detectDragGesturesAfterLongPress(" in text)
print("has draggingSiteId", "draggingSiteId" in text)
print("braces", text.count("{"), text.count("}"))
