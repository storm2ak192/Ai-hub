#!/usr/bin/env python3
"""Force Manage Sites reorder UI to up/down arrow buttons (no drag)."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

# Remove drag state if present
text = text.replace(
    "    val strings = LocalAppStrings.current\n"
    "    var draggingIndex by remember { mutableStateOf<Int?>(null) }\n"
    "    var dragOffsetY by remember { mutableFloatStateOf(0f) }\n\n"
    "    Column(",
    "    val strings = LocalAppStrings.current\n\n"
    "    Column(",
)

# Simplify card modifier (remove graphicsLayer drag visual)
old_card = (
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .graphicsLayer {\n"
    "                            if (draggingIndex == index) {\n"
    "                                translationY = dragOffsetY\n"
    "                                shadowElevation = 18f\n"
    "                                alpha = 0.92f\n"
    "                            }\n"
    "                        }\n"
    "                ) {"
)
new_card = (
    "                    modifier = Modifier.fillMaxWidth()\n"
    "                ) {"
)
if old_card in text:
    text = text.replace(old_card, new_card, 1)
    print("OK: removed drag card graphicsLayer")
elif "modifier = Modifier.fillMaxWidth()" in text:
    print("OK: card already simple")
else:
    print("WARN: card modifier pattern not found")

# Replace DragHandle block with arrow buttons
start = text.find("imageVector = Icons.Filled.DragHandle")
if start != -1:
    # expand to full Icon( ... ) block: find preceding "Icon(" on same indent region
    block_start = text.rfind("Icon(", 0, start)
    # end after the closing of pointerInput / Icon
    # Find the next IconButton after DragHandle block
    clear_pos = text.find("onClick = { onClearSiteSession(site) }", start)
    if block_start < 0 or clear_pos < 0:
        print("ERROR: could not bound DragHandle block")
        raise SystemExit(1)
    # include from Icon( through the blank lines before clear IconButton
    # find start of line for Icon(
    line_start = text.rfind("\n", 0, block_start) + 1
    # find the IconButton line that contains onClearSiteSession
    btn_line = text.rfind("\n", 0, clear_pos)
    btn_start = text.rfind("IconButton(", 0, clear_pos)
    line_btn = text.rfind("\n", 0, btn_start) + 1

    arrows = (
        "                            IconButton(\n"
        "                                onClick = { onMoveOrder(site.id, true) },\n"
        "                                enabled = index > 0,\n"
        "                                modifier = Modifier.testTag(\"move_up_${site.id}\")\n"
        "                            ) {\n"
        "                                Icon(Icons.Filled.ArrowUpward, contentDescription = strings.moveUp)\n"
        "                            }\n"
        "                            IconButton(\n"
        "                                onClick = { onMoveOrder(site.id, false) },\n"
        "                                enabled = index < sites.lastIndex,\n"
        "                                modifier = Modifier.testTag(\"move_down_${site.id}\")\n"
        "                            ) {\n"
        "                                Icon(Icons.Filled.ArrowDownward, contentDescription = strings.moveDown)\n"
        "                            }\n"
    )
    text = text[:line_start] + arrows + text[line_btn:]
    print("OK: replaced DragHandle with arrows")
elif "move_up_" in text and "ArrowUpward" in text:
    print("OK: arrows already present")
else:
    print("WARN: neither DragHandle nor arrows found")

path.write_text(text, encoding="utf-8")
print("has DragHandle icon use", "Icons.Filled.DragHandle" in text)
print("has ArrowUpward", "ArrowUpward" in text)
print("has draggingIndex", "draggingIndex" in text)
print("braces", text.count("{"), text.count("}"))
