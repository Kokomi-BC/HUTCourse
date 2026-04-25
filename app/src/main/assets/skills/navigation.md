---
name: navigation
description: "Campus location skill: locate current position in/out campus, fuzzy search places, estimate route, and launch Amap navigation."
---

# Navigation Skill

## Purpose

- 识别用户位置是否在校内。
- 根据与建筑物的距离返回“在楼内/附近/两楼之间/最近建筑”。
- 支持地点模糊匹配与导航。

## Commands

- `navigation.place.list`: 列出全部可识别地点。
- `navigation.place.search <关键词>`: 按关键词模糊搜索地点。
- `navigation.place.search.hr <关键词>`: 高精度坐标输出（8位小数）。
- `navigation.locate.me`: 判断位置并返回校内语义位置。
- `navigation.coordinate.me`: 返回经纬度坐标。
- `navigation.route.estimate <地点>`: 估算到目标地点的距离与步行时间。
- `navigation.route.amap <地点>`: 生成导航卡片，用户点击后拉起高德地图导航（未安装时跳应用市场）。

## Location Rules

- 优先判断是否在学校边界内；不在则返回“校外”。
- 校内且与建筑距离 <100m：返回“在xx内”。
- 与建筑距离 >100m 且 <200m：返回“在xx附近”或“在xx与xx之间”。
- 若以上都不满足：返回“最近的建筑是xx，距离xxm”。

## Notes

- 地点匹配优先使用别名与楼栋名称，支持自然语言中夹杂“怎么走/去/到”等词。
- `navigation.route.amap` 采用卡片交互，先展示“点击导航至xx”，再由用户主动触发跳转。