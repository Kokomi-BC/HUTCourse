package cn.edu.hut.course;

import android.content.Context;
import android.content.res.AssetManager;

import cn.edu.hut.course.data.AgendaStorageManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillCommandCenter {

    private static final String SKILL_ROOT = "skills";
    private static final Pattern CMD_PATTERN = Pattern.compile(".*?(?i)CMD\\s*[:：]\\s*(.*)$");
    private static final Pattern AGENDA_ID_PATTERN = Pattern.compile("\\[id=(\\d+)]");

    private SkillCommandCenter() {
    }

    public static String buildSkillIndexFromFrontmatter(Context context) {
        Map<String, SkillDoc> docs = loadSkillDocs(context);
        if (docs.isEmpty()) {
            return "无可用技能";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可用技能索引(仅frontmatter):\n");
        for (SkillDoc doc : docs.values()) {
            sb.append("- name: ").append(doc.name)
                    .append(" | description: ").append(doc.description)
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public static String executeCommands(Context context, List<String> commands) {
        return executeCommandsWithFeedback(context, commands).modelFeedback;
    }

    public static CommandBatchResult executeCommandsWithFeedback(Context context, List<String> commands) {
        List<String> normalizedCommands = normalizeBatchCommands(commands);
        if (normalizedCommands.isEmpty()) {
            return new CommandBatchResult("无命令可执行", "无可执行操作");
        }
        StringBuilder modelSb = new StringBuilder();
        List<String> uiLines = new ArrayList<>();
        int index = 1;
        for (String raw : normalizedCommands) {
            SingleExecution single = executeSingle(context, raw);
            modelSb.append(index).append(". ").append(raw).append(" => ").append(single.modelResult).append("\n");
            if (single.userFeedback != null && !single.userFeedback.trim().isEmpty()) {
                uiLines.add(toFormalFeedbackLine(single.userFeedback));
            }
            index++;
        }
        String modelFeedback = modelSb.toString().trim();

        String userFeedback;
        if (uiLines.isEmpty()) {
            userFeedback = "工具执行情况：\n已完成本轮工具调用。";
        } else if (uiLines.size() == 1) {
            userFeedback = "工具执行情况：\n" + uiLines.get(0);
        } else {
            StringBuilder uiSb = new StringBuilder();
            uiSb.append("工具执行情况：\n");
            for (int i = 0; i < uiLines.size(); i++) {
                uiSb.append("- ").append(uiLines.get(i));
                if (i < uiLines.size() - 1) {
                    uiSb.append("\n");
                }
            }
            userFeedback = uiSb.toString();
        }
        return new CommandBatchResult(modelFeedback, userFeedback);
    }

    private static String toFormalFeedbackLine(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "已完成工具调用。";
        }
        String normalized = text.replaceAll("[。！？!?]+$", "");
        if (normalized.startsWith("已")
                || normalized.startsWith("无")
                || normalized.startsWith("存在")
                || normalized.contains("失败")) {
            return normalized + "。";
        }
        return "已完成：" + normalized + "。";
    }

    private static List<String> normalizeBatchCommands(List<String> commands) {
        List<String> normalized = new ArrayList<>();
        if (commands == null || commands.isEmpty()) {
            return normalized;
        }
        for (String raw : commands) {
            String text = raw == null ? "" : raw.trim();
            if (text.isEmpty()) {
                continue;
            }
            List<String> expanded = extractCommands("CMD: " + text);
            if (expanded.isEmpty()) {
                normalized.add(text);
            } else {
                normalized.addAll(expanded);
            }
        }
        return normalized;
    }

    private static SingleExecution executeSingle(Context context, String rawCommand) {
        String cmd = rawCommand == null ? "" : rawCommand.trim();
        if (cmd.isEmpty()) return new SingleExecution("命令为空", "命令为空");

        String lower = cmd.toLowerCase(Locale.ROOT);
        if ("skill.list".equals(lower)) {
            return new SingleExecution(buildSkillIndexFromFrontmatter(context), "已读取技能索引");
        }
        if (lower.startsWith("skill.read ")) {
            String name = cmd.substring("skill.read ".length()).trim();
            return new SingleExecution(readSkillDetail(context, name), "已读取技能详情");
        }
        if ("note.read".equals(lower)) {
            return new SingleExecution(NoteSkillManager.readNotes(context), "读取了笔记");
        }
        if (lower.startsWith("note.write ") || lower.startsWith("note.add ")) {
            int split = cmd.indexOf(' ');
            String payload = split < 0 ? "" : cmd.substring(split + 1).trim();
            String result = NoteSkillManager.appendNote(context, payload);
            return new SingleExecution(result, result.startsWith("已") ? "已新增笔记" : result);
        }
        if (lower.startsWith("note.update ")) {
            String[] parts = cmd.split("\\s+", 3);
            if (parts.length < 3 || !parts[1].matches("\\d+")) {
                return new SingleExecution("修改失败：命令格式应为 note.update <序号> <内容>", "修改失败：命令格式错误");
            }
            int oneBasedIndex = Integer.parseInt(parts[1]);
            String result = NoteSkillManager.updateNoteByIndex(context, oneBasedIndex, parts[2]);
            return new SingleExecution(result, result.startsWith("修改成功") ? "已修改笔记" : result);
        }
        if (lower.startsWith("note.delete ")) {
            String arg = cmd.substring("note.delete ".length()).trim();
            if (arg.matches("\\d+")) {
                String result = NoteSkillManager.deleteNoteByIndex(context, Integer.parseInt(arg));
                return new SingleExecution(result, result.startsWith("删除成功") ? "已删除笔记" : result);
            }
            String result = NoteSkillManager.deleteNoteByKeyword(context, arg);
            return new SingleExecution(result, result.startsWith("删除成功") ? "已删除笔记" : result);
        }
        if ("note.clear".equals(lower)) {
            String result = NoteSkillManager.clearNotes(context);
            return new SingleExecution(result, result.startsWith("已") ? "已清空笔记" : result);
        }

        if ("course.today_remaining".equals(lower) || "course.today.remaining".equals(lower)) {
            String result = CourseQuerySkillManager.queryTodayRemaining(context);
            return new SingleExecution(result, "已查询今日剩余课程");
        }
        if (lower.startsWith("course.date ")) {
            String dateArg = cmd.substring("course.date ".length()).trim();
            String result = CourseQuerySkillManager.queryCoursesByDate(context, dateArg);
            return new SingleExecution(result, "已查询指定日期课程");
        }
        if (lower.startsWith("course.search.name ")) {
            String keyword = cmd.substring("course.search.name ".length()).trim();
            String result = CourseQuerySkillManager.searchByCourseName(context, keyword);
            return new SingleExecution(result, "已按课程名查询课程");
        }
        if (lower.startsWith("course.search ")) {
            String keyword = cmd.substring("course.search ".length()).trim();
            String result = CourseQuerySkillManager.searchByKeyword(context, keyword);
            return new SingleExecution(result, "已按关键词查询课程");
        }

        if ("agenda.read.today".equals(lower)) {
            String result = AgendaSkillManager.readToday(context);
            return new SingleExecution(result, "已查询今日日程");
        }
        if (lower.startsWith("agenda.read.date ")) {
            String dateArg = cmd.substring("agenda.read.date ".length()).trim();
            String result = AgendaSkillManager.readByDate(context, dateArg);
            return new SingleExecution(result, "已查询指定日期日程");
        }
        if (lower.startsWith("agenda.search ")) {
            String keyword = cmd.substring("agenda.search ".length()).trim();
            String result = AgendaSkillManager.search(context, keyword);
            return new SingleExecution(result, "已按关键词查询日程");
        }
        if (lower.startsWith("agenda.create ")) {
            return executeAgendaCreateWithVerification(context, cmd);
        }
        if (lower.startsWith("agenda.update ")) {
            String[] parts = cmd.split("\\s+", 3);
            if (parts.length < 3 || !parts[1].matches("\\d+")) {
                return new SingleExecution("更新失败：命令格式应为 agenda.update <id> <json>", "更新失败：命令格式错误");
            }
            long id = Long.parseLong(parts[1]);
            String result = AgendaSkillManager.update(context, id, parts[2]);
            return new SingleExecution(result, result.startsWith("更新成功") ? "已更新日程" : result);
        }
        if (lower.startsWith("agenda.delete ")) {
            String arg = cmd.substring("agenda.delete ".length()).trim();
            if (!arg.matches("\\d+")) {
                return new SingleExecution("删除失败：命令格式应为 agenda.delete <id>", "删除失败：命令格式错误");
            }
            long id = Long.parseLong(arg);
            String result = AgendaSkillManager.delete(context, id);
            return new SingleExecution(result, result.startsWith("删除成功") ? "已删除日程" : result);
        }

        String unknown = "未知命令，支持: skill.list | skill.read <name> | note.read | note.write <内容> | note.update <序号> <内容> | note.delete <序号或关键词> | note.clear | course.today_remaining | course.date <yyyy-MM-dd> | course.search.name <课程名> | course.search <关键词> | agenda.read.today | agenda.read.date <yyyy-MM-dd> | agenda.search <关键词> | agenda.create <json> | agenda.update <id> <json> | agenda.delete <id>";
        return new SingleExecution(unknown, "存在不支持的命令");
    }

    private static SingleExecution executeAgendaCreateWithVerification(Context context, String cmd) {
        String payload = cmd.substring("agenda.create ".length()).trim();
        int beforeCount = AgendaStorageManager.loadAllAgendas(context).size();

        String result = AgendaSkillManager.create(context, payload);
        if (!result.startsWith("创建成功")) {
            return new SingleExecution(result, result);
        }
        if (isAgendaCreatePersisted(context, result, beforeCount)) {
            return new SingleExecution(result, "已创建日程");
        }

        String retryResult = AgendaSkillManager.create(context, payload);
        if (!retryResult.startsWith("创建成功")) {
            return new SingleExecution(retryResult, retryResult);
        }
        if (isAgendaCreatePersisted(context, retryResult, beforeCount)) {
            return new SingleExecution(retryResult, "已创建日程");
        }

        String verifyFailed = "创建失败：写入校验未通过，请重试";
        return new SingleExecution(verifyFailed, verifyFailed);
    }

    private static boolean isAgendaCreatePersisted(Context context, String result, int beforeCount) {
        long createdId = extractAgendaId(result);
        if (createdId > 0 && AgendaStorageManager.getAgenda(context, createdId) != null) {
            return true;
        }
        return AgendaStorageManager.loadAllAgendas(context).size() > beforeCount;
    }

    private static long extractAgendaId(String result) {
        String text = result == null ? "" : result;
        Matcher matcher = AGENDA_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static String readSkillDetail(Context context, String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return "读取失败：skill名为空";
        }
        Map<String, SkillDoc> docs = loadSkillDocs(context);
        SkillDoc doc = docs.get(skillName.trim().toLowerCase(Locale.ROOT));
        if (doc == null) {
            return "读取失败：未找到技能 " + skillName;
        }
        return doc.fullContent;
    }

    private static Map<String, SkillDoc> loadSkillDocs(Context context) {
        Map<String, SkillDoc> result = new HashMap<>();
        traverseAssets(context.getAssets(), SKILL_ROOT, result);
        return result;
    }

    private static void traverseAssets(AssetManager am, String path, Map<String, SkillDoc> out) {
        try {
            String[] children = am.list(path);
            if (children == null || children.length == 0) {
                if (path.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    String text = readAssetFile(am, path);
                    SkillDoc doc = parseSkillDoc(text, path);
                    if (doc != null && doc.name != null && !doc.name.isEmpty()) {
                        out.put(doc.name.toLowerCase(Locale.ROOT), doc);
                    }
                }
                return;
            }
            for (String child : children) {
                String next = path.isEmpty() ? child : path + "/" + child;
                traverseAssets(am, next, out);
            }
        } catch (Exception ignored) {
        }
    }

    private static String readAssetFile(AssetManager am, String path) {
        try (InputStream is = am.open(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static SkillDoc parseSkillDoc(String markdown, String fallbackPath) {
        if (markdown == null || markdown.trim().isEmpty()) return null;
        String text = markdown.replace("\r\n", "\n");
        int first = text.indexOf("---\n");
        if (first != 0) {
            return null;
        }
        int second = text.indexOf("\n---", 4);
        if (second < 0) return null;

        String frontmatter = text.substring(4, second).trim();
        String name = "";
        String description = "";
        for (String line : frontmatter.split("\n")) {
            String one = line.trim();
            if (one.startsWith("name:")) {
                name = one.substring("name:".length()).trim().replace("\"", "");
            } else if (one.startsWith("description:")) {
                description = one.substring("description:".length()).trim().replace("\"", "");
            }
        }
        if (name.isEmpty()) {
            String fileName = fallbackPath;
            int slash = fileName.lastIndexOf('/');
            if (slash >= 0 && slash < fileName.length() - 1) {
                fileName = fileName.substring(slash + 1);
            }
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                fileName = fileName.substring(0, dot);
            }
            name = fileName;
        }
        return new SkillDoc(name, description, text);
    }

    private static final class SkillDoc {
        final String name;
        final String description;
        final String fullContent;

        SkillDoc(String name, String description, String fullContent) {
            this.name = name;
            this.description = description;
            this.fullContent = fullContent;
        }
    }

    public static final class CommandBatchResult {
        public final String modelFeedback;
        public final String userFeedback;

        CommandBatchResult(String modelFeedback, String userFeedback) {
            this.modelFeedback = modelFeedback;
            this.userFeedback = userFeedback;
        }
    }

    private static final class SingleExecution {
        final String modelResult;
        final String userFeedback;

        SingleExecution(String modelResult, String userFeedback) {
            this.modelResult = modelResult;
            this.userFeedback = userFeedback;
        }
    }

    public static List<String> extractCommands(String modelText) {
        List<String> commands = new ArrayList<>();
        if (modelText == null || modelText.trim().isEmpty()) return commands;
        String normalized = modelText.replace("\r\n", "\n");
        boolean inCmdBlock = false;
        for (String line : normalized.split("\n")) {
            String one = line.trim();
            Matcher matcher = CMD_PATTERN.matcher(one);
            if (matcher.matches()) {
                inCmdBlock = true;
                String payload = matcher.group(1) == null ? "" : matcher.group(1).trim();
                appendExpandedCommands(payload, commands);
                continue;
            }

            if (!inCmdBlock) {
                continue;
            }
            if (one.isEmpty() || one.startsWith("```")) {
                continue;
            }

            String candidate = stripBulletPrefix(one);
            if (isLikelyCommand(candidate)) {
                appendExpandedCommands(candidate, commands);
            } else {
                inCmdBlock = false;
            }
        }
        return commands;
    }

    private static void appendExpandedCommands(String payload, List<String> out) {
        if (payload == null) {
            return;
        }
        String text = payload.trim();
        if (text.isEmpty()) {
            return;
        }
        List<String> parts = splitTopLevelCommands(text);
        for (String part : parts) {
            String candidate = stripBulletPrefix(part.trim());
            if (!candidate.isEmpty()) {
                out.add(candidate);
            }
        }
    }

    private static List<String> splitTopLevelCommands(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;
        int parenDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }

            if ((inSingleQuote || inDoubleQuote) && ch == '\\') {
                current.append(ch);
                escaping = true;
                continue;
            }

            if (!inSingleQuote && ch == '"') {
                inDoubleQuote = !inDoubleQuote;
                current.append(ch);
                continue;
            }
            if (!inDoubleQuote && ch == '\'') {
                inSingleQuote = !inSingleQuote;
                current.append(ch);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '{') {
                    braceDepth++;
                    current.append(ch);
                    continue;
                }
                if (ch == '}' && braceDepth > 0) {
                    braceDepth--;
                    current.append(ch);
                    continue;
                }
                if (ch == '[') {
                    bracketDepth++;
                    current.append(ch);
                    continue;
                }
                if (ch == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    current.append(ch);
                    continue;
                }
                if (ch == '(') {
                    parenDepth++;
                    current.append(ch);
                    continue;
                }
                if (ch == ')' && parenDepth > 0) {
                    parenDepth--;
                    current.append(ch);
                    continue;
                }

                boolean topLevel = braceDepth == 0 && bracketDepth == 0 && parenDepth == 0;
                if (topLevel) {
                    if (ch == ';' || ch == '；') {
                        appendCommandPart(parts, current);
                        current.setLength(0);
                        continue;
                    }
                    if (ch == '&' && i + 1 < text.length() && text.charAt(i + 1) == '&') {
                        appendCommandPart(parts, current);
                        current.setLength(0);
                        i++;
                        continue;
                    }
                    if (ch == '|' && i + 1 < text.length() && text.charAt(i + 1) == '|') {
                        appendCommandPart(parts, current);
                        current.setLength(0);
                        i++;
                        continue;
                    }
                }
            }

            current.append(ch);
        }

        appendCommandPart(parts, current);
        return parts;
    }

    private static void appendCommandPart(List<String> parts, StringBuilder current) {
        String one = current.toString().trim();
        if (!one.isEmpty()) {
            parts.add(one);
        }
    }

    private static String stripBulletPrefix(String line) {
        if (line == null) {
            return "";
        }
        String candidate = line.trim();
        candidate = candidate.replaceFirst("^(?:[-*•]|\\d+[.)])\\s*", "");
        return candidate.trim();
    }

    private static boolean isLikelyCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("skill.")
            || lower.startsWith("note.")
            || lower.startsWith("course.")
            || lower.startsWith("agenda.");
    }
}
