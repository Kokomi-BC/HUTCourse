# 强智教务系统课表 HTML 元素解读指南

本文档旨在详细解读强智教务系统（新版）课表页面的 HTML 结构，为后续 Android 应用开发中的数据解析提供参考。

## 1. 整体布局
课表数据承载于一个类名为 `qz-weeklyTable` 的表格中。

- **表格选择器**: `table.qz-weeklyTable`
- **表头 (thead)**: 包含“周次”和“星期一”至“星期日”。
- **表体 (tbody)**: 包含 5 行（对应 5 大节），每行包含 1 列节次信息和 7 列日期课程信息。

## 2. 核心元素解析

### 2.1 节次/时间列 (`td.qz-weeklyTable-label`)
每行的第一列，包含该大节的名称、包含的小节以及具体时间范围。
```html
<td class="qz-weeklyTable-td qz-weeklyTable-label">
    <div class="index-title">第一大节</div>
    <div class="index-detailtext">(01、02小节)</div>
    <div class="index-detailtext qz-flex-row">
        <span> 08:00~09:40</span>
    </div>
</td>
```
- **解析建议**: 通过 `index-title` 获取大节序，通过 `span` 获取时间段。

### 2.2 课程单元格 (`td.qz-hasCourse`)
如果某个时段有课，`<td>` 会带有 `qz-hasCourse` 类。
```html
<td rowspan="1" name="kbDataTd" class="qz-weeklyTable-td qz-hasCourse" colSize="1">
    <div class="td-cell">
        <ul class="courselists" kbdataSize="1">
            <li class="courselists-item">
                <!-- 课程具体信息 -->
            </li>
        </ul>
    </div>
</td>
```
- **关键属性**:
    - `rowspan`: 课程跨越的节次数（如跨 2 大节则为 2）。
    - `kbdataSize`: 该时段内的课程数量（处理多门课重叠的情况）。
    - `li.courselists-item`: 单个课程的容器。

### 2.3 课程详细内容 (li 内部结构)
虽然附件截断了部分内容，但根据典型结构和截图，信息通常分布在多个 `div` 或 `span` 中：
- **课程名**: 通常在第一个显眼的容器内，可能带有 `title` 属性。
- **老师**: 文本包含“老师：”字样。
- **时间**: 文本包含“时间：”字样（例如：1-11周）。
- **地点**: 文本包含“地点：”字样。

### 2.4 备注信息 (tfoot)
位于表格底部的备注，包含了一些不规则安排的课程（如实习、劳动教育）。
```html
<tfoot class="qz-weeklyTable-tfoot">
    <td class="qz-weeklyTable-detailtext">
        <div class="td-cell">生产实习 郭青松 15-18周; ...</div>
    </td>
</tfoot>
```

## 3. 样式与状态标识
通过 `class` 判定课程性质（对应截图右侧图例）：
- `classtype1`: 必修 (蓝色)
- `classtype2`: 限选 (红色)
- `classtype3`: 任选 (橙色)
- `classtype4`: 公选 (绿色)
- `classtype5`: 选修 (浅蓝色)
- `classtype9`: 其它 (紫色)

## 4. Android 开发解析策略建议

### 4.1 数据抓取
1. **获取源**: 使用 `OkHttp` 获取 HTML 字符串（需携带有效的 `JSESSIONID` Cookie）。
2. **解析库**: 使用 `Jsoup` (Java/Kotlin 开发首选)。

### 4.2 逻辑实现
- **多课合并**: 必须处理 `rowspan` 逻辑。如果第二行的对应列消失了，说明被第一行的 `rowspan="2"` 覆盖了。
- **重叠课**: 遍历 `ul.courselists` 下的所有 `li`，因为同一时间段可能存在不同周次的课程。
- **备注解析**: 注意 `tfoot` 中的文本，通常需要用正则表达式或分号 `;` 分割。

---
**提示**: 教务系统返回的 HTML 可能编码为 `GBK` 或 `UTF-8`，请在 `Jsoup.parse()` 时务必确认。
