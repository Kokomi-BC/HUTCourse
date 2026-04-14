---
name: note
description: "Create and read concise user notes or todos. Keep records brief and actionable, with total stored notes capped at 1000 characters."
---

# Note Skill

## Purpose

- 记录用户的重要事项与待办。
- 支持读取当前记录并返回简明摘要。

## Rules

- 仅记录关键信息，避免长篇解释。
- 单条建议控制在 160 字以内。
- 所有记录累计不超过 1000 字；超出时优先移除最旧条目。

## Commands

- `note.read`: 读取当前事项。
- `note.write <内容>`: 新增一条事项。
- `note.update <序号> <内容>`: 按序号修改事项。
- `note.delete <序号或关键词>`: 按序号或关键词删除事项。
- `note.clear`: 清空所有事项。

## Chat Shortcuts

- `/notes`: 读取当前事项。
- `/note <内容>`: 新增事项。
- `/note-edit <序号> <内容>`: 修改事项。
- `/note-del <序号或关键词>`: 删除事项。
- `/note-clear`: 清空事项。

## Response Style

- 优先使用短句。
- 输出可执行项，不输出冗余背景。
