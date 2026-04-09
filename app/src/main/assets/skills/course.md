---
name: course
description: "Query course schedules by remaining classes today, specific date, or by course-name/keyword search; returns multiple results for model reasoning."
---

# Course Skill

## Purpose

- 查询课表课程信息，支持返回多条结果供模型判断。
- 查询结果包含课程名称、上课时间、地点（如有则带教师）。

## Commands

- `course.today_remaining`: 查询今日剩余课程。
- `course.date <yyyy-MM-dd>`: 查询指定日期的全部课程。
- `course.search.name <课程名>`: 按课程名称查询课程。
- `course.search <关键词>`: 按课程关键词（含课程信息）查询。

## Result Rules

- 默认返回有序列表（按星期/节次排序）。
- 无结果时返回明确“无匹配结果”。
- 日期解析失败时返回格式错误提示。

## Notes

- 周次计算基于开学日期（semester_start_date）。
- 仅查询正常课程，不包含备注课程。
