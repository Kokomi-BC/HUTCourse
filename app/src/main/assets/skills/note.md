---
name: note
description: "Only record key items the user explicitly asks to remember (preferences, constraints, long-term reminders). Keep notes concise and capped at 1000 characters."
---

# Note Skill

## Purpose

- 仅记录用户明确要求“记住/记录”的关键信息。
- 用于长期有效的偏好、约束、重要待办，不记录普通闲聊内容。

## Rules

- 只有在用户明确表达“请记住/帮我记一下/后续按这个来”时才写入。
- 优先记录：用户偏好、固定要求、长期提醒、关键身份/背景约束。
- 不记录：临时对话细节、可即时推断的信息、冗余背景描述。
- 单条控制在 160 字以内，保留可执行关键信息。
- 总长度不超过 1000 字，超出时优先删除最旧且不重要条目。

## Commands

- `note.read`: 读取事项。
- `note.write <内容>`: 新增一条事项。
- `note.update <序号> <内容>`: 按序号修改事项。
- `note.delete <序号或关键词>`: 按序号或关键词删除事项。
- `note.clear`: 清空所有事项。

## Chat Shortcuts

- `/notes`: 读取事项。
- `/note <内容>`: 新增事项。
- `/note-edit <序号> <内容>`: 修改事项。
- `/note-del <序号或关键词>`: 删除事项。
- `/note-clear`: 清空事项。

## Response Style

- 使用短句。
- 先给结果，再给简短状态（如“已记录/已更新/无需记录”）。
- 若内容不属于关键记忆，明确提示“不建议写入长期记录”。
