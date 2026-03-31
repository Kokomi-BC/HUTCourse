package com.example.myapplication;

import android.content.Context;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CourseScraper {

    private static final String TAG = "CourseScraper";
    public static final String BASE_URL = "http://jwxt.hut.edu.cn";
    public static final String LOGIN_URL = BASE_URL + "/jsxsd/sso.jsp";
    public static final String TARGET_URL = BASE_URL + "/jsxsd/xskb/xskb_list.do?viweType=0";
    public static final String EXPERIMENT_URL = BASE_URL + "/jsxsd/syjx/toXskb.do";

    public interface ScrapeCallback {
        void onSuccess(List<Course> courses);
        void onError(String msg);
    }

    public static void extractAllTables(String cookie, ScrapeCallback callback) {
        new Thread(() -> {
            try {
                List<Course> combinedList = new ArrayList<>();
                boolean hasAnySuccess = false;
                try {
                    String normalHtml = fetch(TARGET_URL, cookie);
                    parseRegular(normalHtml, combinedList);
                    hasAnySuccess = true;
                } catch (Exception e) {
                    Log.e(TAG, "Regular parse error", e);
                }

                try {
                    String expHtml = fetch(EXPERIMENT_URL, cookie);
                    parseExperiment(expHtml, combinedList);
                    hasAnySuccess = true;
                } catch (Exception e) {
                    Log.e(TAG, "Experiment parse error", e);
                }

                if (!hasAnySuccess) {
                    callback.onError("未获取到课表页面，请确认已在教务系统登录");
                    return;
                }
                
                List<Course> finalResult = deduplicate(combinedList);
                callback.onSuccess(finalResult);

            } catch (Exception e) {
                Log.e(TAG, "Extraction error", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static void parseRegular(String html, List<Course> out) {
        Document doc = Jsoup.parse(html);
        Elements trs = doc.select(".qz-weeklyTable-thbody .qz-weeklyTable-tr");
        for (int i = 0; i < trs.size(); i++) {
            int startSec = i * 2 + 1; // 0->1, 1->3, 2->5, 3->7, 4->9
            Elements tds = trs.get(i).select("td[name=kbDataTd]");
            for (int j = 0; j < tds.size(); j++) {
                int dayOfWeek = j + 1; // 0->1, 6->7
                Elements courseElements = tds.get(j).select("li.courselists-item");
                for (Element courseElem : courseElements) {
                    parseCourseElement(courseElem, dayOfWeek, startSec, 0, out, "Regular");
                }
            }
        }
        
        Elements tfootTexts = doc.select(".qz-weeklyTable-detailtext .td-cell");
        if (tfootTexts.size() > 0) {
            String bottomText = tfootTexts.get(0).text();
            String[] parts = bottomText.split(";");
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                Course c = new Course();
                c.name = part.trim();
                c.location = ""; 
                c.weeks = parseWeeks(part);
                if (c.weeks.isEmpty()) {
                    for(int w=1; w<=20; w++) c.weeks.add(w);
                }
                c.dayOfWeek = 0;
                c.startSection = 0;
                c.isRemark = true;
                c.isExperimental = false;
                out.add(c);
            }
        }
    }

    private static void parseExperiment(String html, List<Course> out) {
        String sourceLabel = "Experiment";
        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");
        Element mainTable = null;
        for (Element t : tables) {
            if (t.text().contains("第一大节") && t.select("tr").size() >= 4) {
                mainTable = t;
                break;
            }
        }
        if (mainTable == null) {
            Log.w(TAG, "No suitable table found for " + sourceLabel);
            return;
        }

        Elements trs = mainTable.select("tr");
        int maxRows = trs.size();
        int maxCols = 20;
        Element[][] grid = new Element[maxRows][maxCols];

        for (int r = 0; r < maxRows; r++) {
            Element tr = trs.get(r);
            Elements tds = tr.select("> td, > th");
            int ptr = 0;
            for (Element td : tds) {
                while (ptr < maxCols && grid[r][ptr] != null) ptr++;
                if (ptr >= maxCols) break;

                int rowspan = 1, colspan = 1;
                try { if (td.hasAttr("rowspan")) rowspan = Integer.parseInt(td.attr("rowspan").trim()); } catch (Exception ignored) {}
                try { if (td.hasAttr("colspan")) colspan = Integer.parseInt(td.attr("colspan").trim()); } catch (Exception ignored) {}

                for (int i = 0; i < rowspan; i++) {
                    for (int j = 0; j < colspan; j++) {
                        if (r + i < maxRows && ptr + j < maxCols) {
                            grid[r + i][ptr + j] = td;
                        }
                    }
                }
                ptr += colspan;
            }
        }

        int actualCols = 0;
        for (int c = 0; c < maxCols; c++) {
            if (grid[0][c] != null || (maxRows > 1 && grid[1][c] != null)) {
                actualCols = c + 1;
            }
        }

        int[] rowToSession = new int[maxRows];
        for(int r = 0; r < maxRows; r++) {
            for(int c = 0; c < maxCols; c++) {
                if (grid[r][c] == null) continue;
                String txt = grid[r][c].text().trim();
                if (txt.contains("第一大节")) { rowToSession[r] = 1; break; }
                else if (txt.contains("第二大节")) { rowToSession[r] = 3; break; }
                else if (txt.contains("第三大节")) { rowToSession[r] = 5; break; }
                else if (txt.contains("第四大节")) { rowToSession[r] = 7; break; }
                else if (txt.contains("第五大节")) { rowToSession[r] = 9; break; }
                else if (txt.contains("第六大节")) { rowToSession[r] = 11; break; }
            }
            if (r > 0 && rowToSession[r] == 0) rowToSession[r] = rowToSession[r - 1];
        }

        int[] rowToWeek = new int[maxRows];
        for(int r = 0; r < maxRows; r++) {
            for (int c = 0; c < 2 && c < maxCols; c++) {
                if (grid[r][c] != null) {
                    String leftTxt = grid[r][c].text().trim();
                    Matcher m = Pattern.compile("^\\s*(\\d+)\\s*$").matcher(leftTxt);
                    if (m.find()) {
                        rowToWeek[r] = Integer.parseInt(m.group(1));
                        break;
                    }
                }
            }
            if (r > 0 && rowToWeek[r] == 0) rowToWeek[r] = rowToWeek[r - 1];
        }

        int[] colToDay = new int[maxCols];
        for(int r = 0; r < Math.min(3, maxRows); r++) {
            for(int c = 0; c < maxCols; c++) {
                if(grid[r][c] == null) continue;
                String txt = grid[r][c].text().trim();
                if(txt.contains("星期一") || txt.contains("周一")) colToDay[c] = 1;
                else if(txt.contains("星期二") || txt.contains("周二")) colToDay[c] = 2;
                else if(txt.contains("星期三") || txt.contains("周三")) colToDay[c] = 3;
                else if(txt.contains("星期四") || txt.contains("周四")) colToDay[c] = 4;
                else if(txt.contains("星期五") || txt.contains("周五")) colToDay[c] = 5;
                else if(txt.contains("星期六") || txt.contains("周六")) colToDay[c] = 6;
                else if(txt.contains("星期日") || txt.contains("周日")) colToDay[c] = 7;
            }
        }
        
        boolean hasDayHeaders = false;
        for(int d : colToDay) { if (d > 0) hasDayHeaders = true; }
        
        if (!hasDayHeaders) {
             int startCol = actualCols - 7;
             if (startCol < 0) startCol = 0;
             for (int c = startCol; c < actualCols; c++) {
                 colToDay[c] = c - startCol + 1;
             }
        }

        Set<Element> processed = new HashSet<>();
        for (int r = 0; r < maxRows; r++) {
            for (int c = 0; c < maxCols; c++) {
                Element td = grid[r][c];
                if (td == null || !processed.add(td)) continue;

                int dayOfWeek = colToDay[c];
                int startSec = rowToSession[r];
                if (dayOfWeek < 1 || dayOfWeek > 7 || startSec < 1) continue;

                Elements courseElements = td.select("div.kbcontent, div.mini_kb_content, li.courselists-item");
                if (courseElements.isEmpty() && (!td.text().isEmpty() && (td.text().contains("周") || td.text().contains("地点")))) {
                    courseElements.add(td);
                }

                for (Element courseElem : courseElements) {
                    if (courseElem.hasClass("kbcontent") && courseElem.html().contains("----------------------")) {
                        String[] parts = courseElem.html().split("----------------------|<hr[^>]*>");
                        for (String part : parts) {
                            if (part.trim().isEmpty()) continue;
                            Element wrapper = Jsoup.parseBodyFragment("<div>" + part + "</div>").selectFirst("div");
                            parseCourseElement(wrapper, dayOfWeek, startSec, rowToWeek[r], out, sourceLabel);
                        }
                    } else {
                        parseCourseElement(courseElem, dayOfWeek, startSec, rowToWeek[r], out, sourceLabel);
                    }
                }
            }
        }
    }

    private static void parseCourseElement(Element container, int dayOfWeek, int startSection, int blockWeek, List<Course> out, String sourceLabel) {
        String text = container.text().trim();
        if (text.isEmpty() || text.length() < 2) return;
        
        Course c = new Course();
        
        Element nameElem = container.selectFirst("font[title=课程名称], .qz-hasCourse-title, strong, b");
        if (nameElem != null) {
            c.name = nameElem.text().trim();
        } else {
            String[] lines = text.split("\\s+|\\n");
            c.name = lines.length > 0 ? lines[0] : "未知课程";
        }
        
        Element roomElem = container.selectFirst("font[title=教室]");
        if (roomElem != null) {
            c.location = roomElem.text().trim();
        } else {
            Element detailItem = container.selectFirst(".qz-hasCourse-detailitem");
            if (detailItem != null && !detailItem.text().contains("老师") && !detailItem.text().contains("时间")) {
                c.location = detailItem.text().trim();
            } else {
                c.location = regex(text, "地点[：:]([^\\s；;]+)");
                if (c.location.isEmpty()) c.location = regex(text, "@([^\\s]+)");
            }
        }
        
        c.weeks = parseWeeks(text);
        if (c.weeks.isEmpty() && blockWeek > 0) {
            c.weeks.add(blockWeek);
        } else if (c.weeks.isEmpty()) {
            for(int i = 1; i <= 20; i++) c.weeks.add(i); 
        }
        
        c.isExperimental = sourceLabel.equals("Experiment") || text.contains("实验");
        c.dayOfWeek = dayOfWeek;
        c.startSection = startSection;

        Log.d(TAG, "Parsed >> " + c.name + " Wks: " + c.weeks + " (Exp: "+c.isExperimental+")");
        out.add(c);
    }

    private static List<Course> deduplicate(List<Course> list) {
        Map<String, Course> map = new HashMap<>();
        for (Course c : list) {
            if (c.isRemark) {
                String key = "remark|" + c.name;
                if (map.containsKey(key)) {
                    Set<Integer> weeks = new HashSet<>(map.get(key).weeks);
                    weeks.addAll(c.weeks);
                    map.get(key).weeks = new ArrayList<>(weeks);
                    Collections.sort(map.get(key).weeks);
                } else {
                    map.put(key, c);
                }
                continue;
            }
            if (c.dayOfWeek < 1 || c.dayOfWeek > 7) continue;
            String key = c.name + "|" + c.dayOfWeek + "|" + c.startSection + "|" + (c.isExperimental ? "Exp" : "Reg");
            if (map.containsKey(key)) {
                Set<Integer> weeks = new HashSet<>(map.get(key).weeks);
                weeks.addAll(c.weeks);
                map.get(key).weeks = new ArrayList<>(weeks);
                Collections.sort(map.get(key).weeks);
            } else {
                map.put(key, c);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static String fetch(String url, String cookie) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    private static String regex(String s, String p) {
        Matcher m = Pattern.compile(p).matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    private static List<Integer> parseWeeks(String str) {
        Set<Integer> set = new HashSet<>();
        try {
            Matcher m = Pattern.compile("([\\d,，\\-]+)周").matcher(str);
            while (m.find()) {
                String clean = m.group(1).replace("　", "").replace(" ", "");
                for (String seg : clean.split("[,，]")) {
                    if (seg.contains("-")) {
                        String[] r = seg.split("-");
                        if (r.length >= 2) {
                            int start = Integer.parseInt(r[0].trim());
                            int end = Integer.parseInt(r[1].trim());
                            for (int i = start; i <= end; i++) set.add(i);
                        }
                    } else if (!seg.isEmpty()) {
                        set.add(Integer.parseInt(seg.trim()));
                    }
                }
            }
        } catch (Exception ignored) {}
        List<Integer> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
