---
name: agenda
description: "Manage schedule agendas with create/read/search/update/delete, including repeat rules and priority."
---

# Agenda Skill

## Purpose

- 管理日程，支持新增、读取、搜索、更新、删除。
- 支持重复规则：不重复、每天、每周、每月固定日。
- 支持优先级：低、中、高。

## Commands

- agenda.read.today: 查询今日日程。
- `agenda.read.date yyyy-MM-dd`: 查询指定日期日程。
- `agenda.search 关键词`: 在标题与详细描述中搜索日程。
- `agenda.create {json}`: 新增日程。
- `agenda.update id {json}`: 按 id 更新日程。
- `agenda.delete id`: 按 id 删除日程。

## JSON Payload

- title: 待办标题，必填。
- description: 详细描述，可选。
- location: 地点，可选（也兼容 place/site/venue/address/locationName，未填默认无地点）。
- date: 基准日期，格式 yyyy-MM-dd（也兼容 day/dateValue）。
- start: 开始时间，格式 HH:mm（也兼容 startTime/begin/beginTime）。
- end: 结束时间，格式 HH:mm（也兼容 endTime/finish/finishTime）。
- timeRange: 时间段，格式 HH:mm-HH:mm，可替代 start/end。
- priority: low/medium/high 或 1/2/3 或 低/中/高（可选，未填默认 low）。
- repeat: none/daily/weekly/monthly（也兼容 repeatRule/repeatType，可选，未填默认 none）。
- monthlyStrategy: skip/month_end，仅每月固定日时生效（也兼容 shortMonthStrategy/monthlyPolicy）。
- location 规则: 若可匹配校内地点则自动标准化，否则按自定义地点保存。

## Command Style

- 建议一行一个 CMD，避免在一个 JSON 命令中夹杂额外分号与并列命令。

## Example

- agenda.create {"title":"组会","description":"周例会","location":"崇慧楼510","date":"2026-04-15","timeRange":"16:00-17:30","priority":"high","repeat":"weekly"}
- agenda.update 12 {"title":"组会(改)","priority":"medium"}
