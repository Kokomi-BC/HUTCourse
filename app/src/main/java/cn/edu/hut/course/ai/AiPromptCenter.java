package cn.edu.hut.course.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class AiPromptCenter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA);

    private AiPromptCenter() {
    }

    public static String buildSystemPrompt() {
        StringBuilder system = new StringBuilder();
        system.append("你是本地课表AI助手。\n");
        system.append("第一阶段仅使用技能frontmatter索引判断可用工具，不得假设你已知技能全文。\n");
        system.append("如需工具，输出一行或多行命令，格式必须为: CMD: <command>。\n");
        system.append("可用命令: skill.list | skill.read <name> | note.read | note.write <内容> | note.update <序号> <内容> | note.delete <序号或关键词> | note.clear | course.today_remaining | course.date <yyyy-MM-dd> | course.search.teacher <姓名> | course.search.name <课程名> | course.search <关键词>。\n");
        system.append("当信息足够时直接输出最终答复，不要输出CMD。\n");
        return system.toString();
    }

    public static String buildFirstTurnUserPrompt(String skillIndex, String selectedText, String userText, boolean includeCurrentTime) {
        StringBuilder userPrompt = new StringBuilder();
        if (includeCurrentTime) {
            userPrompt.append("[当前时间]\n")
                    .append(LocalDateTime.now().format(TIME_FORMATTER))
                    .append("\n\n");
        }
        userPrompt.append("[技能索引]\n").append(skillIndex).append("\n\n");
        if (selectedText != null && !selectedText.trim().isEmpty()) {
            userPrompt.append("[用户选中文本]\n").append(selectedText.trim()).append("\n\n");
        }
        userPrompt.append("[用户问题]\n").append(userText).append("\n");
        return userPrompt.toString();
    }

    public static String buildToolFollowupPrompt(String userText, String previousAssistantOutput,
                                                 List<String> commands, String commandResult) {
        StringBuilder nextPrompt = new StringBuilder();
        nextPrompt.append("[用户原始问题]\n").append(userText).append("\n\n");
        nextPrompt.append("[上轮模型输出]\n").append(previousAssistantOutput).append("\n\n");
        nextPrompt.append("[本轮命令]\n");
        for (String c : commands) {
            nextPrompt.append("- ").append(c).append("\n");
        }
        nextPrompt.append("\n[命令返回值]\n").append(commandResult).append("\n\n");
        nextPrompt.append("如果仍需要更多工具，继续输出CMD行；否则输出最终答复。\n");
        return nextPrompt.toString();
    }
}
