---
name: classroom
description: "Query empty public classrooms by time range and query today's usage by room name; login invalid state is returned by query result."
---

# Classroom Skill

## Purpose

- 查询教务系统空教室与教室使用情况。
- 仅关注公共教室（教室名以“公共”开头）。

## Commands

- `classroom.empty.query <json>`: 按时段查询空教室（最多返回10条）。
- `classroom.usage.today <公共xxx>`: 查询指定公共教室今日使用情况。

## Query Rules

- 空教室工具不再提供单独登录校验命令。
- 若查询返回“未登录/登录失效”，应提示用户前往登录教务系统并继续后续查询。
- 空教室查询固定使用：
  - 借用模式：按时间借用（`jyms=1`）
  - 校区：河西校区（`xqbh=2`）
  - 学年学期：自动读取教务页面当前可用学年学期
  - 节次范围：01-10（内部映射为 0102/0304/0506/0708/0910）
- 空教室结果只保留：
  - 查询节次时段均为空的教室
  - 教室名以“公共”开头
  - 最多10条

## JSON Payload

- day: 查询星期，可填 `1-7`、`周一..周日`、`today`（可选，默认今天）。
- date: 查询日期，格式 `yyyy-MM-dd`（可选，填了将自动推导星期）。
- timeRange: 时段，格式 `HH:mm-HH:mm`（推荐）。
- start / end: 开始与结束时间，格式 `HH:mm`（可替代 `timeRange`）。
- slotStart / slotEnd: 节次起止，范围 `1-10`（可替代时间）。
- selectJc: 直接节次编码（如 `0102,0304`，可选）。

## Examples

- `classroom.empty.query {"day":"today","timeRange":"10:00-11:40"}`
- `classroom.empty.query {"day":"周三","slotStart":3,"slotEnd":6}`
- `classroom.usage.today 公共505`
