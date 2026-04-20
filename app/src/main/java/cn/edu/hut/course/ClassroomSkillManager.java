package cn.edu.hut.course;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClassroomSkillManager {

    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String QUERY_PAGE_URL = BASE_URL + "/jsxsd/kbxx/jsjy_query";
    private static final String QUERY_ACTION_URL = BASE_URL + "/jsxsd/kbxx/jsjy_query2";

    private static final int CAMPUS_HEXI = 2;
    private static final int MAX_EMPTY_ROOMS_RETURN = 10;

    private static final String[] SLOT_CODES = {"0102", "0304", "0506", "0708", "0910"};
    private static final String[] SLOT_LABELS = {"01-02", "03-04", "05-06", "07-08", "09-10"};
    private static final int[] SLOT_START_MINUTES = {8 * 60, 10 * 60, 14 * 60, 16 * 60, 19 * 60};
    private static final int[] SLOT_END_MINUTES = {9 * 60 + 40, 11 * 60 + 40, 15 * 60 + 40, 17 * 60 + 40, 20 * 60 + 40};

    private ClassroomSkillManager() {
    }

    public static String queryLoginStatus(Context context) {
        QueryContext queryContext = prepareQueryContext();
        if (queryContext == null) {
            return notLoggedInMessage();
        }
        return "教务登录状态：已登录，可执行空教室查询";
    }

    public static String queryEmptyRoomsByTime(Context context, String payload) {
        EmptyRoomRequest request = parseEmptyRoomRequest(payload);
        if (!request.valid) {
            return "查询失败：" + request.error;
        }

        QueryContext queryContext = prepareQueryContext();
        if (queryContext == null) {
            return notLoggedInMessage();
        }

        try {
            String responseBody = runRoomQuery(queryContext, request.date, request.date, request.dayOfWeek, request.slotIndexes, "");

            JsonRoomRowsResult jsonRowsResult = parseRoomRowsFromJson(responseBody);
            if (jsonRowsResult.recognized) {
                List<RoomInfo> matchedFromJson = collectEmptyPublicRoomsFromJson(jsonRowsResult.rows);
                return formatEmptyRoomQueryResult(request, matchedFromJson);
            }

            TablePair tablePair = findRoomTables(Jsoup.parse(responseBody));
            if (!tablePair.valid()) {
                return "查询失败：教务返回内容格式暂不支持，请稍后重试";
            }

            List<RoomInfo> matched = collectEmptyPublicRooms(tablePair, request.dayOfWeek, request.slotIndexes);
            return formatEmptyRoomQueryResult(request, matched);
        } catch (Exception e) {
            return "查询失败：" + safe(e.getMessage());
        }
    }

    public static String queryRoomTodayUsage(Context context, String roomNameRaw) {
        String roomName = normalizeRoomName(roomNameRaw);
        if (roomName.isEmpty()) {
            return "查询失败：教室名称为空，请使用 classroom.usage.today 公共xxx";
        }
        if (!roomName.startsWith("公共")) {
            return "查询失败：仅支持公共教室，请使用“公共xxx”格式";
        }

        QueryContext queryContext = prepareQueryContext();
        if (queryContext == null) {
            return notLoggedInMessage();
        }

        Calendar now = Calendar.getInstance();
        int today = toMondayFirstDay(now);
        String todayDate = formatDate(now);

        try {
            RoomUsage usageByJson = queryRoomUsageByJson(queryContext, roomName, todayDate, today);
            if (usageByJson != null) {
                return formatRoomUsageResult(usageByJson, todayDate, today);
            }

            List<Integer> allSlots = new ArrayList<>();
            for (int i = 0; i < SLOT_CODES.length; i++) {
                allSlots.add(i);
            }

            String html = runRoomQuery(queryContext, "", "", today, allSlots, "");
            TablePair tablePair = findRoomTables(Jsoup.parse(html));
            if (!tablePair.valid()) {
                return "查询失败：未解析到教室使用表格，请稍后重试";
            }

            RoomUsage usage = findRoomUsage(tablePair, roomName, today);
            if (usage == null) {
                return "查询结果：未找到教室 " + roomName + "（仅支持公共教室）";
            }

            return formatRoomUsageResult(usage, todayDate, today);
        } catch (Exception e) {
            return "查询失败：" + safe(e.getMessage());
        }
    }

    public static String alignEmptyQueryPayloadWithUserText(String payload, String userText) {
        JSONObject json = parseJson(payload);
        if (json == null) {
            return payload;
        }

        Integer relativeOffset = parseRelativeDayOffset(userText);
        if (relativeOffset == null) {
            return payload;
        }

        Calendar targetDate = startOfToday();
        targetDate.add(Calendar.DAY_OF_MONTH, relativeOffset);
        try {
            json.put("date", formatDate(targetDate));
            json.put("day", toMondayFirstDay(targetDate));
            return json.toString();
        } catch (Exception ignored) {
            return payload;
        }
    }

    private static RoomUsage queryRoomUsageByJson(QueryContext queryContext,
                                                  String roomName,
                                                  String date,
                                                  int dayOfWeek) throws Exception {
        List<Boolean> occupiedBySlot = new ArrayList<>();
        boolean recognizedAll = true;
        boolean foundRoom = false;

        for (int slot = 0; slot < SLOT_CODES.length; slot++) {
            String responseBody = runRoomQuery(queryContext, date, date, dayOfWeek, Collections.singletonList(slot), "");
            JsonRoomRowsResult parsed = parseRoomRowsFromJson(responseBody);
            if (!parsed.recognized) {
                recognizedAll = false;
                break;
            }
            JsonRoomRow row = findRoomFromJsonRows(parsed.rows, roomName);
            if (row == null) {
                occupiedBySlot.add(false);
                continue;
            }
            foundRoom = true;
            occupiedBySlot.add(hasUsageMarker(row.usageMarker));
        }

        if (!recognizedAll) {
            return null;
        }
        if (!foundRoom) {
            return null;
        }
        return new RoomUsage(roomName, occupiedBySlot);
    }

    private static String formatRoomUsageResult(RoomUsage usage, String date, int dayOfWeek) {
        StringBuilder sb = new StringBuilder();
        sb.append("教室今日使用情况（")
                .append(usage.roomName)
                .append("，")
                .append(date)
                .append("，")
                .append(toWeekdayText(dayOfWeek))
                .append("）:\n");

        List<String> occupiedSlots = new ArrayList<>();
        List<String> freeSlots = new ArrayList<>();
        for (int i = 0; i < usage.slotOccupied.size(); i++) {
            boolean occupied = usage.slotOccupied.get(i);
            String label = SLOT_LABELS[i];
            sb.append(i + 1)
                    .append(". ")
                    .append(label)
                    .append("：")
                    .append(occupied ? "占用" : "空闲");
            if (i < usage.slotOccupied.size() - 1) {
                sb.append("\n");
            }
            if (occupied) {
                occupiedSlots.add(label);
            } else {
                freeSlots.add(label);
            }
        }

        sb.append("\n空闲节次：")
                .append(freeSlots.isEmpty() ? "无" : String.join(",", freeSlots))
                .append("\n占用节次：")
                .append(occupiedSlots.isEmpty() ? "无" : String.join(",", occupiedSlots));
        return sb.toString();
    }

    private static String formatEmptyRoomQueryResult(EmptyRoomRequest request, List<RoomInfo> matched) {
        StringBuilder sb = new StringBuilder();
        sb.append("空教室查询（河西校区，")
                .append(request.date)
                .append("，")
                .append(toWeekdayText(request.dayOfWeek))
                .append("，节次 ")
                .append(buildSlotLabel(request.slotIndexes))
                .append("）:");

        if (matched.isEmpty()) {
            sb.append("无匹配空教室");
            return sb.toString();
        }

        int displayCount = Math.min(MAX_EMPTY_ROOMS_RETURN, matched.size());
        sb.append("\n");
        for (int i = 0; i < displayCount; i++) {
            RoomInfo room = matched.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(room.name);
            if (!safe(room.type).isEmpty()) {
                sb.append(" | ").append(room.type);
            }
            if (i < displayCount - 1) {
                sb.append("\n");
            }
        }
        if (matched.size() > displayCount) {
            sb.append("\n共").append(matched.size()).append("间，仅展示前").append(displayCount).append("间");
        }
        return sb.toString();
    }

    private static List<RoomInfo> collectEmptyPublicRoomsFromJson(List<JsonRoomRow> rows) {
        List<RoomInfo> matched = new ArrayList<>();
        for (JsonRoomRow row : rows) {
            if (row == null) {
                continue;
            }
            String roomName = normalizeRoomName(row.roomName);
            if (!roomName.startsWith("公共")) {
                continue;
            }
            if (hasUsageMarker(row.usageMarker)) {
                continue;
            }
            matched.add(new RoomInfo(roomName, safe(row.roomType).trim(), safe(row.seatText).trim()));
        }
        return matched;
    }

    private static JsonRoomRow findRoomFromJsonRows(List<JsonRoomRow> rows, String roomName) {
        String normalizedTarget = normalizeRoomName(roomName);
        for (JsonRoomRow row : rows) {
            if (row == null) {
                continue;
            }
            if (normalizeRoomName(row.roomName).equals(normalizedTarget)) {
                return row;
            }
        }
        return null;
    }

    private static boolean hasUsageMarker(String marker) {
        String text = safe(marker).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || "null".equals(text)) {
            return false;
        }
        if (text.contains("<iconpark-icon")
                || text.contains("shangke")
                || text.contains("jieyong")
                || text.contains("kaoshi")
                || text.contains("diaoke")) {
            return true;
        }
        return !text.replace("&nbsp;", "").trim().isEmpty();
    }

    private static JsonRoomRowsResult parseRoomRowsFromJson(String responseBody) {
        String trimmed = safe(responseBody).trim();
        if (trimmed.isEmpty() || (!trimmed.startsWith("[") && !trimmed.startsWith("{"))) {
            return JsonRoomRowsResult.notRecognized();
        }

        try {
            JSONArray top = new JSONArray(trimmed);
            JSONArray roomRows = top.optJSONArray(4);
            if (roomRows == null) {
                return JsonRoomRowsResult.recognized(new ArrayList<>());
            }

            List<JsonRoomRow> parsedRows = new ArrayList<>();
            for (int i = 0; i < roomRows.length(); i++) {
                JSONArray row = roomRows.optJSONArray(i);
                if (row == null || row.length() == 0) {
                    continue;
                }

                String roomName = row.optString(0, "");
                String usageMarker = row.isNull(1) ? "" : row.optString(1, "");
                String seatText = row.optString(3, "");
                String roomType = row.optString(4, "");
                if (!safe(roomName).trim().isEmpty()) {
                    parsedRows.add(new JsonRoomRow(roomName, usageMarker, seatText, roomType));
                }
            }
            return JsonRoomRowsResult.recognized(parsedRows);
        } catch (Exception ignored) {
            return JsonRoomRowsResult.notRecognized();
        }
    }

    private static QueryContext prepareQueryContext() {
        try {
            String cookie = getCookie();
            if (cookie.isEmpty()) {
                return null;
            }

            FetchResult queryPage = fetchGet(QUERY_PAGE_URL, cookie);
            if (isLoginResponse(queryPage)) {
                return null;
            }
            if (queryPage.code != HttpURLConnection.HTTP_OK) {
                return null;
            }

            QueryMeta meta = parseQueryMeta(queryPage.body);
            if (meta == null) {
                return null;
            }
            return new QueryContext(cookie, meta);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static QueryMeta parseQueryMeta(String html) {
        Document doc = Jsoup.parse(safe(html));

        String xnxqh = selectedValueById(doc, "xnxq");
        if (xnxqh.isEmpty()) {
            xnxqh = valueById(doc, "xnxqh");
        }

        String kbjcmsid = selectedValueById(doc, "kbjcmsid");
        String typewhere = valueById(doc, "typewhere");
        String qsxq = valueById(doc, "qsxq");

        if (xnxqh.isEmpty()) {
            return null;
        }
        if (kbjcmsid.isEmpty()) {
            kbjcmsid = "94D51EECEBF4F9B4E053474110AC8060";
        }
        if (typewhere.isEmpty()) {
            typewhere = "jszq";
        }
        if (qsxq.isEmpty()) {
            qsxq = "1";
        }
        return new QueryMeta(xnxqh, kbjcmsid, typewhere, qsxq);
    }

    private static String runRoomQuery(QueryContext queryContext,
                                       String startDate,
                                       String endDate,
                                       int dayOfWeek,
                                       List<Integer> slotIndexes,
                                       String jsbh) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("xnxqh", queryContext.meta.xnxqh);
        params.put("xqbh", String.valueOf(CAMPUS_HEXI));
        params.put("jxqbh", "");
        params.put("jxlbh", "");
        params.put("jsbh", safe(jsbh));
        params.put("jslx", "");
        params.put("bjfh", "=");
        params.put("rnrs", "");
        params.put("yx", "");
        params.put("kbjcmsid", queryContext.meta.kbjcmsid);
        params.put("selectZc", "");
        params.put("startdate", safe(startDate));
        params.put("enddate", safe(endDate));
        params.put("selectXq", String.valueOf(dayOfWeek));
        params.put("selectJc", buildSlotCodeCsv(slotIndexes));
        params.put("syjs0601id", "");
        params.put("typewhere", queryContext.meta.typewhere);
        params.put("qsxq", queryContext.meta.qsxq);
        params.put("jyms", "1");

        FetchResult result = fetchPostForm(QUERY_ACTION_URL, queryContext.cookie, params);
        if (isLoginResponse(result)) {
            throw new IllegalStateException("未登录或登录已失效，请先登录教务系统");
        }
        if (result.code != HttpURLConnection.HTTP_OK || safe(result.body).isEmpty()) {
            throw new IllegalStateException("教务系统查询失败，状态码=" + result.code);
        }
        return result.body;
    }

    private static List<RoomInfo> collectEmptyPublicRooms(TablePair tablePair, int dayOfWeek, List<Integer> slotIndexes) {
        List<Integer> targetColumns = toUsageColumns(dayOfWeek, slotIndexes);
        List<RoomInfo> matched = new ArrayList<>();

        int rowCount = Math.min(tablePair.nameRows.size(), tablePair.usageRows.size());
        for (int i = 0; i < rowCount; i++) {
            Elements nameCells = tablePair.nameRows.get(i).select(">th,>td");
            if (nameCells.size() < 1) {
                continue;
            }
            String roomName = normalizeRoomName(nameCells.get(0).text());
            if (!roomName.startsWith("公共")) {
                continue;
            }

            Elements usageCells = tablePair.usageRows.get(i).select(">th,>td");
            if (usageCells.isEmpty()) {
                continue;
            }

            boolean allEmpty = true;
            for (int col : targetColumns) {
                if (col < 0 || col >= usageCells.size()) {
                    allEmpty = false;
                    break;
                }
                if (isCellOccupied(usageCells.get(col))) {
                    allEmpty = false;
                    break;
                }
            }
            if (!allEmpty) {
                continue;
            }

            String roomType = nameCells.size() > 1 ? safe(nameCells.get(1).text()).trim() : "";
            String seatText = nameCells.size() > 2 ? safe(nameCells.get(2).text()).trim() : "";
            matched.add(new RoomInfo(roomName, roomType, seatText));
        }
        return matched;
    }

    private static RoomUsage findRoomUsage(TablePair tablePair, String targetRoomName, int dayOfWeek) {
        String normalizedTarget = normalizeRoomName(targetRoomName);
        int rowCount = Math.min(tablePair.nameRows.size(), tablePair.usageRows.size());

        for (int i = 0; i < rowCount; i++) {
            Elements nameCells = tablePair.nameRows.get(i).select(">th,>td");
            if (nameCells.size() < 1) {
                continue;
            }
            String roomName = normalizeRoomName(nameCells.get(0).text());
            if (!roomName.equals(normalizedTarget)) {
                continue;
            }

            Elements usageCells = tablePair.usageRows.get(i).select(">th,>td");
            int offset = (dayOfWeek - 1) * SLOT_CODES.length;
            if (usageCells.size() < offset + SLOT_CODES.length) {
                return null;
            }

            List<Boolean> occupied = new ArrayList<>();
            for (int s = 0; s < SLOT_CODES.length; s++) {
                occupied.add(isCellOccupied(usageCells.get(offset + s)));
            }
            return new RoomUsage(roomName, occupied);
        }
        return null;
    }

    private static boolean isCellOccupied(Element cell) {
        if (cell == null) {
            return false;
        }
        if (!cell.select("iconpark-icon").isEmpty()) {
            return true;
        }
        String text = safe(cell.text()).trim();
        return !text.isEmpty();
    }

    private static TablePair findRoomTables(Document doc) {
        Element nameTable = null;
        Element usageTable = null;

        for (Element table : doc.select("table")) {
            Elements rows = table.select("tr");
            if (rows.size() < 20) {
                continue;
            }
            int firstRowCellCount = rows.get(0).select(">th,>td").size();
            if (firstRowCellCount == 3 && nameTable == null) {
                nameTable = table;
                continue;
            }
            if (firstRowCellCount >= 35 && usageTable == null) {
                usageTable = table;
            }
        }

        if (nameTable == null || usageTable == null) {
            return new TablePair(null, null);
        }
        return new TablePair(nameTable.select("tr"), usageTable.select("tr"));
    }

    private static List<Integer> toUsageColumns(int dayOfWeek, List<Integer> slotIndexes) {
        int offset = Math.max(0, dayOfWeek - 1) * SLOT_CODES.length;
        List<Integer> cols = new ArrayList<>();
        for (int slot : slotIndexes) {
            if (slot >= 0 && slot < SLOT_CODES.length) {
                cols.add(offset + slot);
            }
        }
        return cols;
    }

    private static EmptyRoomRequest parseEmptyRoomRequest(String payload) {
        JSONObject json = parseJson(payload);
        if (json == null) {
            return EmptyRoomRequest.error("payload 需为 JSON 对象");
        }

        String dateText = firstString(json,
                "date",
                "dayDate",
                "targetDate",
                "queryDate");

        Object dayValue = firstObject(json,
                "day",
                "weekday",
                "dayOfWeek",
                "weekDay",
                "xq");

        int dayOfWeek = parseDayValue(dayValue);
        Integer relativeOffset = parseRelativeDayOffset(dayValue);

        Calendar date = null;
        if (relativeOffset != null) {
            date = startOfToday();
            date.add(Calendar.DAY_OF_MONTH, relativeOffset);
            dayOfWeek = toMondayFirstDay(date);
        } else if (!dateText.isEmpty()) {
            date = parseDate(dateText);
            if (date == null) {
                return EmptyRoomRequest.error("date 格式错误，需为 yyyy-MM-dd");
            }
            dayOfWeek = toMondayFirstDay(date);
        } else if (dayOfWeek >= 1 && dayOfWeek <= 7) {
            date = resolveDateForWeekday(dayOfWeek);
        }

        if (date == null) {
            date = startOfToday();
        }

        if (dayOfWeek < 1 || dayOfWeek > 7) {
            dayOfWeek = toMondayFirstDay(date);
        }

        List<Integer> slotIndexes = parseSlotIndexes(json);
        if (slotIndexes.isEmpty()) {
            return EmptyRoomRequest.error("请提供时段参数，如 timeRange/start+end 或 slotStart+slotEnd（01-10节）");
        }

        return EmptyRoomRequest.ok(formatDate(date), dayOfWeek, slotIndexes);
    }

    private static List<Integer> parseSlotIndexes(JSONObject json) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();

        String slotCodeRaw = firstString(json,
                "selectJc",
                "slotCodes",
                "slotCode",
                "jc");
        if (!slotCodeRaw.isEmpty()) {
            for (String token : splitTokens(slotCodeRaw)) {
                int idx = indexOfSlotCode(token);
                if (idx >= 0) {
                    result.add(idx);
                }
            }
        }

        int startSection = parseSection(firstObject(json,
                "slotStart",
                "sectionStart",
                "startSection"));
        int endSection = parseSection(firstObject(json,
                "slotEnd",
                "sectionEnd",
                "endSection"));
        if (startSection > 0 && endSection > 0 && endSection >= startSection) {
            for (int sec = startSection; sec <= endSection; sec++) {
                int idx = (sec - 1) / 2;
                if (idx >= 0 && idx < SLOT_CODES.length) {
                    result.add(idx);
                }
            }
        }

        String range = firstString(json,
                "timeRange",
                "range",
                "period");
        if (!range.isEmpty()) {
            int[] pair = parseTimeRange(range);
            if (pair != null) {
                addSlotsByTime(result, pair[0], pair[1]);
            }
        }

        String startTime = firstString(json,
                "start",
                "startTime",
                "begin",
                "beginTime");
        String endTime = firstString(json,
                "end",
                "endTime",
                "finish",
                "finishTime");
        if (!startTime.isEmpty() && !endTime.isEmpty()) {
            int startMinute = parseMinute(startTime);
            int endMinute = parseMinute(endTime);
            if (startMinute >= 0 && endMinute > startMinute) {
                addSlotsByTime(result, startMinute, endMinute);
            }
        }

        return new ArrayList<>(result);
    }

    private static void addSlotsByTime(Set<Integer> slotIndexes, int startMinute, int endMinute) {
        for (int i = 0; i < SLOT_CODES.length; i++) {
            if (startMinute < SLOT_END_MINUTES[i] && endMinute > SLOT_START_MINUTES[i]) {
                slotIndexes.add(i);
            }
        }
    }

    private static int parseDayValue(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Number) {
            int day = ((Number) value).intValue();
            return day >= 1 && day <= 7 ? day : -1;
        }

        String text = safe(String.valueOf(value)).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return -1;
        }

        if (text.matches("[1-7]")) {
            return Integer.parseInt(text);
        }
        if (text.contains("today") || text.contains("今天") || text.contains("今日") || text.contains("今晚")) {
            return toMondayFirstDay(Calendar.getInstance());
        }
        if (text.contains("明天") || text.contains("明日") || text.contains("次日") || text.contains("明晚")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            return toMondayFirstDay(cal);
        }
        if (text.contains("后天")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 2);
            return toMondayFirstDay(cal);
        }
        if (text.contains("大后天")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 3);
            return toMondayFirstDay(cal);
        }

        if (text.contains("星期一") || text.contains("周一") || text.contains("礼拜一") || text.equals("mon") || text.equals("monday")) return 1;
        if (text.contains("星期二") || text.contains("周二") || text.contains("礼拜二") || text.equals("tue") || text.equals("tuesday")) return 2;
        if (text.contains("星期三") || text.contains("周三") || text.contains("礼拜三") || text.equals("wed") || text.equals("wednesday")) return 3;
        if (text.contains("星期四") || text.contains("周四") || text.contains("礼拜四") || text.equals("thu") || text.equals("thursday")) return 4;
        if (text.contains("星期五") || text.contains("周五") || text.contains("礼拜五") || text.equals("fri") || text.equals("friday")) return 5;
        if (text.contains("星期六") || text.contains("周六") || text.contains("礼拜六") || text.equals("sat") || text.equals("saturday")) return 6;
        if (text.contains("星期日") || text.contains("星期天") || text.contains("周日") || text.contains("周天") || text.contains("礼拜日") || text.equals("sun") || text.equals("sunday")) return 7;

        return -1;
    }

    private static Integer parseRelativeDayOffset(Object value) {
        if (value == null) {
            return null;
        }
        String text = safe(String.valueOf(value)).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains("today") || text.contains("今天") || text.contains("今日") || text.contains("今晚")) {
            return 0;
        }
        if (text.contains("明天") || text.contains("明日") || text.contains("次日") || text.contains("明晚")) {
            return 1;
        }
        if (text.contains("大后天")) {
            return 3;
        }
        if (text.contains("后天")) {
            return 2;
        }
        return null;
    }

    private static int parseSection(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Number) {
            int section = ((Number) value).intValue();
            return (section >= 1 && section <= 10) ? section : -1;
        }
        String text = safe(String.valueOf(value)).replaceAll("[^0-9]", "");
        if (text.isEmpty()) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(text);
            return (parsed >= 1 && parsed <= 10) ? parsed : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int[] parseTimeRange(String text) {
        String raw = safe(text).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String normalized = raw.replace("~", "-").replace("—", "-").replace("－", "-").replace("到", "-");
        String[] parts = normalized.split("-");
        if (parts.length != 2) {
            return null;
        }
        int start = parseMinute(parts[0]);
        int end = parseMinute(parts[1]);
        if (start < 0 || end <= start) {
            return null;
        }
        return new int[]{start, end};
    }

    private static int parseMinute(String text) {
        String raw = safe(text).trim();
        if (!raw.matches("^\\d{1,2}:\\d{2}$")) {
            return -1;
        }
        String[] parts = raw.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String buildSlotCodeCsv(List<Integer> slotIndexes) {
        if (slotIndexes == null || slotIndexes.isEmpty()) {
            return String.join(",", SLOT_CODES);
        }
        List<String> codes = new ArrayList<>();
        for (int idx : slotIndexes) {
            if (idx >= 0 && idx < SLOT_CODES.length) {
                codes.add(SLOT_CODES[idx]);
            }
        }
        return String.join(",", codes);
    }

    private static String buildSlotLabel(List<Integer> slotIndexes) {
        if (slotIndexes == null || slotIndexes.isEmpty()) {
            return String.join(",", SLOT_LABELS);
        }
        List<String> labels = new ArrayList<>();
        for (int idx : slotIndexes) {
            if (idx >= 0 && idx < SLOT_LABELS.length) {
                labels.add(SLOT_LABELS[idx]);
            }
        }
        return labels.isEmpty() ? String.join(",", SLOT_LABELS) : String.join(",", labels);
    }

    private static int indexOfSlotCode(String token) {
        String cleaned = safe(token).trim();
        for (int i = 0; i < SLOT_CODES.length; i++) {
            if (SLOT_CODES[i].equals(cleaned)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTokens(String raw) {
        List<String> out = new ArrayList<>();
        for (String one : safe(raw).split("[，,;；\\s|]+")) {
            String token = one.trim();
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    private static String selectedValueById(Document doc, String id) {
        Element select = doc.selectFirst("select#" + id);
        if (select == null) {
            return "";
        }
        Element selected = select.selectFirst("option[selected]");
        if (selected == null) {
            selected = select.selectFirst("option");
        }
        if (selected == null) {
            return "";
        }
        String value = safe(selected.attr("value")).trim();
        if (!value.isEmpty()) {
            return value;
        }
        return safe(selected.text()).trim();
    }

    private static String valueById(Document doc, String id) {
        Element node = doc.selectFirst("#" + id);
        if (node == null) {
            return "";
        }
        String value = safe(node.attr("value")).trim();
        if (!value.isEmpty()) {
            return value;
        }
        return safe(node.text()).trim();
    }

    private static FetchResult fetchGet(String url, String cookie) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int code = conn.getResponseCode();
            String location = safe(conn.getHeaderField("Location"));
            String body = readBody(conn);
            return new FetchResult(code, location, body);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static FetchResult fetchPostForm(String url, String cookie, Map<String, String> params) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Referer", QUERY_PAGE_URL);
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            String body = buildFormBody(params);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String location = safe(conn.getHeaderField("Location"));
            String responseBody = readBody(conn);
            return new FetchResult(code, location, responseBody);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String buildFormBody(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> one : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(one.getKey(), StandardCharsets.UTF_8.name()))
                    .append('=')
                    .append(URLEncoder.encode(safe(one.getValue()), StandardCharsets.UTF_8.name()));
        }
        return sb.toString();
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (Exception ignored) {
            is = conn.getErrorStream();
        }
        if (is == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static boolean isLoginResponse(FetchResult result) {
        if (result == null) {
            return true;
        }
        String location = safe(result.location).toLowerCase(Locale.ROOT);
        if (location.contains("mycas.hut.edu.cn/cas") || location.contains("/cas/login") || location.contains("/jsxsd/sso.jsp")) {
            return true;
        }
        String body = safe(result.body).toLowerCase(Locale.ROOT);
        return body.contains("mycas.hut.edu.cn/cas")
                || body.contains("/cas/login")
                || body.contains("统一身份认证")
                || body.contains("登录 - 湖南工业大学");
    }

    private static String getCookie() {
        CookieManager manager = CookieManager.getInstance();
        String cookie = manager.getCookie(QUERY_PAGE_URL);
        if (cookie == null || cookie.trim().isEmpty()) {
            cookie = manager.getCookie(BASE_URL);
        }
        return cookie == null ? "" : cookie.trim();
    }

    private static JSONObject parseJson(String payload) {
        try {
            String text = safe(payload).trim();
            if (text.isEmpty()) {
                return null;
            }
            return new JSONObject(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object firstObject(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            if (!json.has(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value == null || JSONObject.NULL.equals(value)) {
                continue;
            }
            return value;
        }
        return null;
    }

    private static String firstString(JSONObject json, String... keys) {
        Object value = firstObject(json, keys);
        return value == null ? "" : safe(String.valueOf(value)).trim();
    }

    private static Calendar parseDate(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(value));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatDate(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private static Calendar startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static Calendar resolveDateForWeekday(int targetWeekday) {
        Calendar cal = startOfToday();
        int currentWeekday = toMondayFirstDay(cal);
        int delta = targetWeekday - currentWeekday;
        if (delta < 0) {
            delta += 7;
        }
        cal.add(Calendar.DAY_OF_MONTH, delta);
        return cal;
    }

    private static int toMondayFirstDay(Calendar cal) {
        int raw = cal.get(Calendar.DAY_OF_WEEK);
        return raw == Calendar.SUNDAY ? 7 : raw - 1;
    }

    private static String toWeekdayText(int day) {
        switch (day) {
            case 1:
                return "星期一";
            case 2:
                return "星期二";
            case 3:
                return "星期三";
            case 4:
                return "星期四";
            case 5:
                return "星期五";
            case 6:
                return "星期六";
            case 7:
                return "星期日";
            default:
                return "星期?";
        }
    }

    private static String normalizeRoomName(String raw) {
        return safe(raw).replaceAll("\\s+", "").trim();
    }

    private static String notLoggedInMessage() {
        return "教务登录状态：未登录或已失效，请先在“设置-账号与教务”完成登录";
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class QueryContext {
        final String cookie;
        final QueryMeta meta;

        QueryContext(String cookie, QueryMeta meta) {
            this.cookie = cookie;
            this.meta = meta;
        }
    }

    private static final class QueryMeta {
        final String xnxqh;
        final String kbjcmsid;
        final String typewhere;
        final String qsxq;

        QueryMeta(String xnxqh, String kbjcmsid, String typewhere, String qsxq) {
            this.xnxqh = xnxqh;
            this.kbjcmsid = kbjcmsid;
            this.typewhere = typewhere;
            this.qsxq = qsxq;
        }
    }

    private static final class FetchResult {
        final int code;
        final String location;
        final String body;

        FetchResult(int code, String location, String body) {
            this.code = code;
            this.location = location;
            this.body = body;
        }
    }

    private static final class TablePair {
        final Elements nameRows;
        final Elements usageRows;

        TablePair(Elements nameRows, Elements usageRows) {
            this.nameRows = nameRows;
            this.usageRows = usageRows;
        }

        boolean valid() {
            return nameRows != null && usageRows != null && !nameRows.isEmpty() && !usageRows.isEmpty();
        }
    }

    private static final class RoomInfo {
        final String name;
        final String type;
        final String seatText;

        RoomInfo(String name, String type, String seatText) {
            this.name = name;
            this.type = type;
            this.seatText = seatText;
        }
    }

    private static final class RoomUsage {
        final String roomName;
        final List<Boolean> slotOccupied;

        RoomUsage(String roomName, List<Boolean> slotOccupied) {
            this.roomName = roomName;
            this.slotOccupied = slotOccupied;
        }
    }

    private static final class JsonRoomRow {
        final String roomName;
        final String usageMarker;
        final String seatText;
        final String roomType;

        JsonRoomRow(String roomName, String usageMarker, String seatText, String roomType) {
            this.roomName = roomName;
            this.usageMarker = usageMarker;
            this.seatText = seatText;
            this.roomType = roomType;
        }
    }

    private static final class JsonRoomRowsResult {
        final boolean recognized;
        final List<JsonRoomRow> rows;

        JsonRoomRowsResult(boolean recognized, List<JsonRoomRow> rows) {
            this.recognized = recognized;
            this.rows = rows;
        }

        static JsonRoomRowsResult recognized(List<JsonRoomRow> rows) {
            return new JsonRoomRowsResult(true, rows);
        }

        static JsonRoomRowsResult notRecognized() {
            return new JsonRoomRowsResult(false, new ArrayList<>());
        }
    }

    private static final class EmptyRoomRequest {
        final boolean valid;
        final String error;
        final String date;
        final int dayOfWeek;
        final List<Integer> slotIndexes;

        private EmptyRoomRequest(boolean valid, String error, String date, int dayOfWeek, List<Integer> slotIndexes) {
            this.valid = valid;
            this.error = error;
            this.date = date;
            this.dayOfWeek = dayOfWeek;
            this.slotIndexes = slotIndexes;
        }

        static EmptyRoomRequest ok(String date, int dayOfWeek, List<Integer> slotIndexes) {
            return new EmptyRoomRequest(true, "", date, dayOfWeek, slotIndexes);
        }

        static EmptyRoomRequest error(String error) {
            return new EmptyRoomRequest(false, error, "", -1, new ArrayList<>());
        }
    }
}
