package cn.edu.hut.course;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemorySkillManager {

    private static final String MEMORY_DIR = "skills";
    private static final String MEMORY_FILE = "memory_user.md";
    private static final int MAX_CHARS = 1000;
    private static final Pattern PREFIX_NUMBER = Pattern.compile("^\\d+[\\.、)]\\s*");
    private static final Pattern BRACKET_TIME = Pattern.compile("^\\[(.+?)]\\s*(.*)$");

    private MemorySkillManager() {
    }

    private static File getMemoryFile(Context context) {
        File dir = new File(context.getFilesDir(), MEMORY_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, MEMORY_FILE);
    }

    public static synchronized String readMemories(Context context) {
        try {
            List<String> lines = loadStoredLines(context);
            if (lines.isEmpty()) {
                return "暂无记录";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1).append(". ").append(lines.get(i));
                if (i < lines.size() - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "读取失败：" + e.getMessage();
        }
    }

    public static synchronized String appendMemory(Context context, String rawInput) {
        String cleaned = sanitize(rawInput);
        if (cleaned.isEmpty()) {
            return "记录失败：内容为空";
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String line = "[" + timestamp + "] " + cleaned;
        List<String> lines;
        try {
            lines = loadStoredLines(context);
        } catch (IOException e) {
            return "记录失败：" + e.getMessage();
        }
        lines.add(line);
        return saveLines(context, lines, "已记录");
    }

    public static synchronized String clearMemories(Context context) {
        File file = getMemoryFile(context);
        try {
            Files.write(file.toPath(), new byte[0]);
            return "已清空全部事项";
        } catch (IOException e) {
            return "清空失败：" + e.getMessage();
        }
    }

    public static synchronized String deleteMemoryByIndex(Context context, int oneBasedIndex) {
        List<String> lines;
        try {
            lines = loadStoredLines(context);
        } catch (IOException e) {
            return "删除失败：" + e.getMessage();
        }
        if (lines.isEmpty()) {
            return "删除失败：暂无记录";
        }
        if (oneBasedIndex < 1 || oneBasedIndex > lines.size()) {
            return "删除失败：序号超出范围";
        }
        lines.remove(oneBasedIndex - 1);
        return saveLines(context, lines, "删除成功");
    }

    public static synchronized String deleteMemoryByKeyword(Context context, String keyword) {
        List<String> lines;
        try {
            lines = loadStoredLines(context);
        } catch (IOException e) {
            return "删除失败：" + e.getMessage();
        }
        if (lines.isEmpty()) {
            return "删除失败：暂无记录";
        }

        String key = keyword == null ? "" : keyword.trim();
        if (key.isEmpty()) {
            return "删除失败：关键词为空";
        }

        int before = lines.size();
        lines.removeIf(line -> line.contains(key));
        int removed = before - lines.size();
        if (removed <= 0) {
            return "删除失败：未匹配到关键词";
        }
        return saveLines(context, lines, "删除成功，共移除" + removed + "条");
    }

    public static synchronized String updateMemoryByIndex(Context context, int oneBasedIndex, String rawInput) {
        String cleaned = sanitize(rawInput);
        if (cleaned.isEmpty()) {
            return "修改失败：内容为空";
        }

        List<String> lines;
        try {
            lines = loadStoredLines(context);
        } catch (IOException e) {
            return "修改失败：" + e.getMessage();
        }
        if (lines.isEmpty()) {
            return "修改失败：暂无记录";
        }
        if (oneBasedIndex < 1 || oneBasedIndex > lines.size()) {
            return "修改失败：序号超出范围";
        }

        String current = lines.get(oneBasedIndex - 1);
        Matcher matcher = BRACKET_TIME.matcher(current);
        String timestamp;
        if (matcher.matches()) {
            timestamp = matcher.group(1);
        } else {
            timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        }
        lines.set(oneBasedIndex - 1, "[" + timestamp + "] " + cleaned);
        return saveLines(context, lines, "修改成功");
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        String text = input.replaceAll("\\s+", " ").trim();
        if (text.length() > 160) {
            text = text.substring(0, 160).trim();
        }
        return text;
    }

    private static List<String> loadStoredLines(Context context) throws IOException {
        File file = getMemoryFile(context);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        String existing = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        List<String> lines = new ArrayList<>();
        if (!existing.isEmpty()) {
            for (String one : existing.split("\\n")) {
                String normalized = normalizeStoredLine(one);
                if (!normalized.isEmpty()) {
                    lines.add(normalized);
                }
            }
        }
        return lines;
    }

    private static String normalizeStoredLine(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            return "";
        }
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        line = PREFIX_NUMBER.matcher(line).replaceFirst("").trim();
        return line;
    }

    private static String saveLines(Context context, List<String> lines, String successPrefix) {
        String merged = enforceLimit(lines);
        File file = getMemoryFile(context);
        try {
            Files.write(file.toPath(), merged.getBytes(StandardCharsets.UTF_8));
            if (merged.isEmpty()) {
                return successPrefix + "，暂无记录";
            }
            return successPrefix + "，共" + merged.length() + "字";
        } catch (IOException e) {
            return "写入失败：" + e.getMessage();
        }
    }

    private static String enforceLimit(List<String> lines) {
        String joined = String.join("\n", lines);
        while (joined.length() > MAX_CHARS && !lines.isEmpty()) {
            lines.remove(0);
            joined = String.join("\n", lines);
        }
        return joined;
    }
}
