package cn.edu.hut.course;

import android.content.Context;

import cn.edu.hut.course.data.CampusBuildingStore;
import cn.edu.hut.course.data.AgendaStorageManager;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgendaSkillManager {

    private AgendaSkillManager() {
    }

    public static String readToday(Context context) {
        String today = AgendaStorageManager.formatDate(Calendar.getInstance());
        return readByDate(context, today);
    }

    public static String readByDate(Context context, String dateStr) {
        String date = safe(dateStr).trim();
        Calendar parsed = AgendaStorageManager.parseDateOrNull(date);
        if (parsed == null) {
            return "查询失败：日期格式错误，格式应为 yyyy-MM-dd";
        }

        List<Agenda> agendas = AgendaStorageManager.queryAgendasByDate(context, parsed);
        if (agendas.isEmpty()) {
            return "日程查询(" + AgendaStorageManager.formatDate(parsed) + ")：无日程";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("日程查询(").append(AgendaStorageManager.formatDate(parsed)).append("):\n");
        for (int i = 0; i < agendas.size(); i++) {
            Agenda agenda = agendas.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(buildAgendaLine(agenda, true));
            if (i < agendas.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String search(Context context, String keyword) {
        String key = safe(keyword).trim();
        if (key.isEmpty()) {
            return "查询失败：关键词为空";
        }

        List<Agenda> agendas = AgendaStorageManager.searchAgendas(context, key);
        if (agendas.isEmpty()) {
            return "日程搜索：无匹配结果（关键词=" + key + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("日程搜索（关键词=").append(key).append("):\n");
        for (int i = 0; i < agendas.size(); i++) {
            Agenda agenda = agendas.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(buildAgendaLine(agenda, false));
            if (i < agendas.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String create(Context context, String payload) {
        JSONObject json = parsePayload(payload);
        if (json == null) {
            return "创建失败：payload 需为 JSON 对象";
        }

        Agenda agenda = new Agenda();
        String validation = applyPayload(context, agenda, json, true);
        if (!validation.isEmpty()) {
            return "创建失败：" + validation;
        }

        long id = AgendaStorageManager.createAgenda(context, agenda);
        if (id <= 0) {
            return "创建失败：写入数据库失败";
        }
        Agenda created = AgendaStorageManager.getAgenda(context, id);
        if (created == null) {
            created = agenda;
            created.id = id;
        }
        return "创建成功：" + buildAgendaLine(created, false);
    }

    public static String update(Context context, long agendaId, String payload) {
        if (agendaId <= 0) {
            return "更新失败：id 无效";
        }

        Agenda target = AgendaStorageManager.getAgenda(context, agendaId);
        if (target == null) {
            return "更新失败：未找到 id=" + agendaId + " 的日程";
        }

        JSONObject json = parsePayload(payload);
        if (json == null) {
            return "更新失败：payload 需为 JSON 对象";
        }

        String validation = applyPayload(context, target, json, false);
        if (!validation.isEmpty()) {
            return "更新失败：" + validation;
        }

        boolean ok = AgendaStorageManager.updateAgenda(context, target);
        if (!ok) {
            return "更新失败：写入数据库失败";
        }
        Agenda latest = AgendaStorageManager.getAgenda(context, agendaId);
        if (latest == null) {
            latest = target;
        }
        return "更新成功：" + buildAgendaLine(latest, false);
    }

    public static String delete(Context context, long agendaId) {
        if (agendaId <= 0) {
            return "删除失败：id 无效";
        }
        Agenda target = AgendaStorageManager.getAgenda(context, agendaId);
        if (target == null) {
            return "删除失败：未找到 id=" + agendaId + " 的日程";
        }

        boolean ok = AgendaStorageManager.deleteAgenda(context, agendaId);
        if (!ok) {
            return "删除失败：数据库操作未生效";
        }
        return "删除成功：id=" + agendaId + " " + safe(target.title);
    }

    private static String applyPayload(Context context, Agenda agenda, JSONObject json, boolean createMode) {
        if (agenda == null || json == null) {
            return "参数为空";
        }

        if (createMode || hasAnyKey(json, "title", "todoTitle", "name")) {
            agenda.title = safe(firstString(json, agenda.title, "title", "todoTitle", "name")).trim();
        }
        if (createMode || hasAnyKey(json, "description", "detail", "desc", "content")) {
            agenda.description = safe(firstString(json, agenda.description, "description", "detail", "desc", "content")).trim();
        }
        if (createMode || hasAnyKey(json, "date", "day", "dateValue")) {
            agenda.date = safe(firstString(json, agenda.date, "date", "day", "dateValue")).trim();
        }
        boolean hasLocation = hasAnyKey(json, "location", "place", "site", "venue", "address", "locationName");
        if (createMode && !hasLocation) {
            agenda.location = "";
        }
        if (createMode || hasLocation) {
            String locationRaw = safe(firstString(json, agenda.location, "location", "place", "site", "venue", "address", "locationName"));
            agenda.location = normalizeLocation(context, locationRaw);
        }

        int[] parsedRange = parseTimeRange(json);
        if (parsedRange != null) {
            agenda.startMinute = parsedRange[0];
            agenda.endMinute = parsedRange[1];
        } else {
            if (createMode || hasAnyKey(json, "start", "startTime", "begin", "beginTime", "from")) {
                String startRaw = safe(firstString(json, "", "start", "startTime", "begin", "beginTime", "from")).trim();
                if (startRaw.isEmpty()) {
                    return "start 不能为空";
                }
                int parsed = parseMinute(startRaw);
                if (parsed < 0) {
                    return "start 格式错误，需为 HH:mm";
                }
                agenda.startMinute = parsed;
            }
            if (createMode || hasAnyKey(json, "end", "endTime", "finish", "finishTime", "to")) {
                String endRaw = safe(firstString(json, "", "end", "endTime", "finish", "finishTime", "to")).trim();
                if (endRaw.isEmpty()) {
                    return "end 不能为空";
                }
                int parsed = parseMinute(endRaw);
                if (parsed < 0) {
                    return "end 格式错误，需为 HH:mm";
                }
                agenda.endMinute = parsed;
            }
        }

        boolean hasPriority = hasAnyKey(json, "priority", "priorityLevel", "level");
        if (createMode && !hasPriority) {
            agenda.priority = Agenda.PRIORITY_LOW;
        }
        if (hasPriority) {
            int parsed = parsePriority(firstObject(json, "priority", "priorityLevel", "level"));
            if (parsed < 0) {
                return "priority 仅支持 low/medium/high 或 1/2/3";
            }
            agenda.priority = parsed;
        }

        boolean hasRepeat = hasAnyKey(json, "repeat", "repeatRule", "repeatType");
        if (createMode && !hasRepeat) {
            agenda.repeatRule = Agenda.REPEAT_NONE;
        }
        if (hasRepeat) {
            String parsed = parseRepeat(firstString(json, "", "repeat", "repeatRule", "repeatType"));
            if (parsed.isEmpty()) {
                return "repeat 仅支持 none/daily/weekly/monthly";
            }
            agenda.repeatRule = parsed;
        }

        if (createMode || hasAnyKey(json, "monthlyStrategy", "shortMonthStrategy", "monthlyPolicy", "missingDayStrategy")) {
            String parsed = parseMonthlyStrategy(firstString(json, "", "monthlyStrategy", "shortMonthStrategy", "monthlyPolicy", "missingDayStrategy"));
            if (parsed.isEmpty()) {
                return "monthlyStrategy 仅支持 skip/month_end";
            }
            agenda.monthlyStrategy = parsed;
        }

        if (agenda.title.trim().isEmpty()) {
            return "title 不能为空";
        }

        Calendar date = AgendaStorageManager.parseDateOrNull(agenda.date);
        if (date == null) {
            return "date 格式错误，需为 yyyy-MM-dd";
        }

        if (agenda.startMinute < 0 || agenda.startMinute >= 24 * 60
                || agenda.endMinute <= 0 || agenda.endMinute > 24 * 60
                || agenda.endMinute <= agenda.startMinute) {
            return "时间段非法，要求 00:00 <= start < end <= 24:00";
        }

        if (agenda.priority < Agenda.PRIORITY_LOW || agenda.priority > Agenda.PRIORITY_HIGH) {
            return "priority 超出范围";
        }

        if (Agenda.REPEAT_MONTHLY.equals(agenda.repeatRule)) {
            String strategy = parseMonthlyStrategy(agenda.monthlyStrategy);
            if (strategy.isEmpty()) {
                return "monthlyStrategy 必填，且需为 skip/month_end";
            }
            agenda.monthlyStrategy = strategy;
        } else {
            agenda.monthlyStrategy = Agenda.MONTHLY_SKIP;
        }

        return "";
    }

    private static JSONObject parsePayload(String payload) {
        String raw = safe(payload).trim();
        if (raw.isEmpty()) {
            return null;
        }

        String normalized = normalizePayload(raw);
        JSONObject strict = tryParseObject(normalized);
        if (strict != null) {
            return strict;
        }

        String repaired = repairLooseJson(normalized);
        return tryParseObject(repaired);
    }

    private static int[] parseTimeRange(JSONObject json) {
        if (json == null) {
            return null;
        }
        String range = safe(firstString(json, "", "timeRange", "timeSlot", "period", "time")).trim();
        if (range.isEmpty()) {
            return null;
        }
        String[] parts = range.split("\\s*(?:-|~|～|—|–|到|至)\\s*");
        if (parts.length != 2) {
            return null;
        }
        int start = parseMinute(parts[0].trim());
        int end = parseMinute(parts[1].trim());
        if (start < 0 || end < 0) {
            return null;
        }
        return new int[]{start, end};
    }

    private static int parseMinute(String timeText) {
        String input = safe(timeText).trim().replace('：', ':').replace('点', ':').replace('。', '.');
        if (input.isEmpty()) {
            return -1;
        }

        if (input.matches("^\\d{3,4}$")) {
            int raw = Integer.parseInt(input);
            int hour = raw / 100;
            int minute = raw % 100;
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return hour * 60 + minute;
            }
            return -1;
        }

        try {
            Matcher matcher = Pattern.compile("(\\d{1,2})\\s*[:\\.]\\s*(\\d{1,2})").matcher(input);
            int hour = -1;
            int minute = -1;
            while (matcher.find()) {
                hour = Integer.parseInt(matcher.group(1));
                minute = Integer.parseInt(matcher.group(2));
            }
            if (hour < 0 || minute < 0) {
                return -1;
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int parsePriority(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Number) {
            int num = ((Number) value).intValue();
            if (num >= 1 && num <= 3) {
                return num;
            }
            return -1;
        }

        String raw = safe(String.valueOf(value)).trim().toLowerCase(Locale.ROOT);
        if (raw.contains("低")) {
            return Agenda.PRIORITY_LOW;
        }
        if (raw.contains("高") || raw.contains("紧急")) {
            return Agenda.PRIORITY_HIGH;
        }
        if (raw.contains("中")) {
            return Agenda.PRIORITY_MEDIUM;
        }
        switch (raw) {
            case "1":
            case "low":
            case "l":
            case "低":
                return Agenda.PRIORITY_LOW;
            case "2":
            case "medium":
            case "mid":
            case "m":
            case "中":
                return Agenda.PRIORITY_MEDIUM;
            case "3":
            case "high":
            case "h":
            case "高":
            case "紧急":
                return Agenda.PRIORITY_HIGH;
            default:
                return -1;
        }
    }

    private static String parseRepeat(String repeat) {
        String raw = safe(repeat).trim().toLowerCase(Locale.ROOT);
        if (raw.contains("每月")) {
            return Agenda.REPEAT_MONTHLY;
        }
        if (raw.contains("每周")) {
            return Agenda.REPEAT_WEEKLY;
        }
        if (raw.contains("每天")) {
            return Agenda.REPEAT_DAILY;
        }
        if (raw.contains("不重复")) {
            return Agenda.REPEAT_NONE;
        }
        switch (raw) {
            case "":
            case "none":
            case "不重复":
                return Agenda.REPEAT_NONE;
            case "daily":
            case "每天":
                return Agenda.REPEAT_DAILY;
            case "weekly":
            case "每周":
                return Agenda.REPEAT_WEEKLY;
            case "monthly":
            case "monthly_fixed_day":
            case "每月":
            case "每月固定日":
                return Agenda.REPEAT_MONTHLY;
            default:
                return "";
        }
    }

    private static String parseMonthlyStrategy(String strategy) {
        String raw = safe(strategy).trim().toLowerCase(Locale.ROOT);
        if (raw.contains("月底") || raw.contains("月末")) {
            return Agenda.MONTHLY_MONTH_END;
        }
        if (raw.contains("跳过")) {
            return Agenda.MONTHLY_SKIP;
        }
        switch (raw) {
            case "":
            case "skip":
            case "跳过":
            case "short_skip":
                return Agenda.MONTHLY_SKIP;
            case "month_end":
            case "monthend":
            case "月底":
            case "last_day":
                return Agenda.MONTHLY_MONTH_END;
            default:
                return "";
        }
    }

    private static String normalizeLocation(Context context, String rawLocation) {
        String raw = safe(rawLocation).trim();
        while (raw.startsWith("@") || raw.startsWith("＠")) {
            raw = raw.substring(1).trim();
        }
        if (raw.isEmpty()) {
            return "";
        }
        if (context == null) {
            return raw;
        }

        CampusBuildingStore.ResolvedLocation resolved = CampusBuildingStore.resolveLocation(context, raw);
        if (resolved == null) {
            return raw;
        }
        String merged = CampusBuildingStore.buildLocationText(resolved.buildingName, resolved.roomNumber);
        return merged.isEmpty() ? safe(resolved.buildingName) : merged;
    }

    private static String buildAgendaLine(Agenda agenda, boolean includeDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("[id=").append(agenda.id).append("] ")
                .append(formatMinute(agenda.startMinute))
                .append("-")
                .append(formatMinute(agenda.endMinute))
                .append(" | ")
                .append(priorityText(agenda.priority))
                .append(" | ")
                .append(repeatText(agenda.repeatRule, agenda.monthlyStrategy))
                .append(" | ")
                .append(safe(agenda.title));

        if (!safe(agenda.location).trim().isEmpty()) {
            sb.append(" | 地点 ").append(agenda.location.trim());
        }

        if (includeDescription && !safe(agenda.description).trim().isEmpty()) {
            sb.append(" | ").append(agenda.description.trim());
        }
        if (!includeDescription) {
            sb.append(" | 日期 ").append(safe(agenda.date));
        }
        return sb.toString();
    }

    private static String formatMinute(int minute) {
        int normalized = Math.max(0, Math.min(24 * 60, minute));
        int hour = normalized / 60;
        int min = normalized % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hour, min);
    }

    private static String priorityText(int priority) {
        switch (priority) {
            case Agenda.PRIORITY_LOW:
                return "低";
            case Agenda.PRIORITY_HIGH:
                return "高";
            case Agenda.PRIORITY_MEDIUM:
            default:
                return "中";
        }
    }

    private static String repeatText(String repeatRule, String monthlyStrategy) {
        String repeat = safe(repeatRule).toLowerCase(Locale.ROOT);
        switch (repeat) {
            case Agenda.REPEAT_DAILY:
                return "每天";
            case Agenda.REPEAT_WEEKLY:
                return "每周";
            case Agenda.REPEAT_MONTHLY:
                if (Agenda.MONTHLY_MONTH_END.equals(safe(monthlyStrategy).toLowerCase(Locale.ROOT))) {
                    return "每月固定日(短月改月底)";
                }
                return "每月固定日(短月跳过)";
            case Agenda.REPEAT_NONE:
            default:
                return "不重复";
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static boolean hasAnyKey(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (key != null && json.has(key)) {
                return true;
            }
        }
        return false;
    }

    private static String firstString(JSONObject json, String fallback, String... keys) {
        Object value = firstObject(json, keys);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Object firstObject(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !json.has(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value != null && value != JSONObject.NULL) {
                return value;
            }
        }
        return null;
    }

    private static String normalizePayload(String raw) {
        String text = safe(raw).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json|JSON)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }

        return text
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'')
                .trim();
    }

    private static JSONObject tryParseObject(String text) {
        try {
            return new JSONObject(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String repairLooseJson(String text) {
        String fixed = safe(text);
        fixed = fixed.replaceAll("([\\{,]\\s*)'([^'\\n]+?)'\\s*:", "$1\"$2\":");
        fixed = fixed.replaceAll(":\\s*'([^'\\n]*?)'\\s*([,}])", ":\"$1\"$2");
        fixed = fixed.replaceAll("([\\{,]\\s*)([A-Za-z_][A-Za-z0-9_]*)\\s*:", "$1\"$2\":");
        fixed = fixed.replaceAll(",\\s*([}\\]])", "$1");
        return fixed;
    }
}
