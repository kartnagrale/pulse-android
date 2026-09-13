from pathlib import Path

path = Path("kartik-todo/app/src/main/java/com/kartik/bloom/MainActivity.kt")
text = path.read_text(encoding="utf-8")
old = '            val dayTasks = tasks.filter { it.due == selected.toString() }.sortedByDescending { smartTaskScore(it, selected) }'
new = '            val dayTasks = tasks.filter { it.due == selected.toString() || it.lastCompleted == selected.toString() }.distinctBy { it.id }.sortedByDescending { smartTaskScore(it, selected) }'
if old not in text:
    raise RuntimeError("Calendar day task target not found")
path.write_text(text.replace(old, new), encoding="utf-8")
print("Calendar now keeps completed recurring occurrences visible on their completion date")
