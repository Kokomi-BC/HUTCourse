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
        system.append("你是湖南工业大学课表AI助手。\n");
        system.append("第一阶段仅使用技能frontmatter索引判断可用工具，不得假设你已知技能全文。\n");
        system.append("如需工具，输出一行或多行命令，格式必须为: CMD: <command>。\n");
        system.append("可用命令: skill.list | skill.read <name> | note.read | note.write <内容> | note.update <序号> <内容> | note.delete <序号或关键词> | note.clear | course.today_remaining | course.date <yyyy-MM-dd> | course.search.name <课程名> | course.search <关键词> | navigation.place.list | navigation.place.search <关键词> | navigation.locate.me | navigation.coordinate.me | navigation.route.estimate <地点> | navigation.route.amap <地点> | tavily.search <关键词> | classroom.empty.query <json> | classroom.usage.today <公共xxx> | agenda.read.today | agenda.read.date <yyyy-MM-dd> | agenda.search <关键词> | agenda.create <json> | agenda.update <id> <json> | agenda.delete <id>。\n");
        system.append("agenda.create/agenda.update 的 JSON 字段建议使用: title, description, location, date, start/end（也兼容 startTime/endTime）, priority, repeat, monthlyStrategy；时间格式统一为 HH:mm；priority/repeat/location 可省略，默认 low/none/空地点。\n");
        system.append("涉及空教室查询时直接调用 classroom.empty.query 或 classroom.usage.today；若命令返回未登录/登录失效，不要再做登录校验命令，直接引导用户点击登录卡片继续。\n");
        system.append("classroom.empty.query 的 JSON 建议字段: day(1-7/周几/today)、timeRange(HH:mm-HH:mm) 或 start/end(HH:mm) 或 slotStart/slotEnd(1-10)，date(yyyy-MM-dd，可选)。\n");
        system.append("classroom.usage.today 仅能查询今天；若用户询问明天/后天/指定日期，必须改用 classroom.empty.query。\n");
        system.append("location 可留空；若为校内地点会自动标准化（楼栋+房间），否则按自定义地点保存。\n");
        system.append("用户询问\"我在哪/我在学校哪里\"时优先调用 navigation.locate.me；用户询问\"怎么走/怎么到某地\"时优先调用 navigation.route.amap，若仅需文字距离再调用 navigation.route.estimate。\n");
        system.append("当用户问题依赖实时互联网信息（如新闻/网页资料/外部事实）时，可调用 tavily.search。\n");
        system.append("最多连续调用工具30轮；达到上限后停止工具调用，直接给出当前可得结论。\n");
        system.append("除非用户明确要求删除/清空，否则不要调用 agenda.delete、note.delete、note.clear。\n");
        system.append("当信息足够时输出最终答复，不要输出CMD。\n");
        system.append("若本轮要继续调用工具，只输出CMD行，不要输出TITLE。\n");
        system.append("是否需要TITLE由用户提示中的[标题策略]决定。\n");
        system.append("时间语义规则: 用户提到明天/明日/次日时，优先使用 course.date <下一自然日>，不要用 course.today_remaining。\n");
        system.append("当时间在 00:00-05:59，'今天'仍指当前自然日，'明天/明日'必须是下一自然日。\n");
        system.append("最终答复末尾补一句与当前问题强相关的简短反问，推动下一步（例如：要不要我顺便帮你生成明晚自习规划？）。\n");
        system.append("最终答复不要出现工具调用过程口播（如'已查询...'、'已调用...'），只输出对用户有用的结论。\n");
        return system.toString();
    }

    public static String buildFirstTurnUserPrompt(String skillIndex, String userText,
                                                  boolean includeCurrentTime, boolean requestTitleInFinalAnswer) {
        StringBuilder userPrompt = new StringBuilder();
        if (includeCurrentTime) {
            userPrompt.append("[当前24小时制时间]\n")
                    .append(LocalDateTime.now().format(TIME_FORMATTER))
                    .append("\n\n");
        }
        userPrompt.append("[标题策略]\n");
        if (requestTitleInFinalAnswer) {
            userPrompt.append("这是新会话首轮，需要本次对话主题。最终答复必须先输出一行: TITLE: <20字以内标题>，下一行留空，再输出正文。\n\n");
        } else {
            userPrompt.append("直接输出正文即可。\n\n");
        }
        userPrompt.append("[技能索引]\n").append(skillIndex).append("\n\n");
        userPrompt.append("[用户问题]\n").append(userText).append("\n");
        return userPrompt.toString();
    }

    public static String buildToolFollowupPrompt(String userText, String previousAssistantOutput,
                                                 List<String> commands, String commandResult,
                                                 boolean requestTitleInFinalAnswer) {
        StringBuilder nextPrompt = new StringBuilder();
        nextPrompt.append("[用户原始问题]\n").append(userText).append("\n\n");
        nextPrompt.append("[上轮模型输出]\n").append(previousAssistantOutput).append("\n\n");
        nextPrompt.append("[本轮命令]\n");
        for (String c : commands) {
            nextPrompt.append("- ").append(c).append("\n");
        }
        nextPrompt.append("\n[命令返回值]\n").append(commandResult).append("\n\n");
        if (requestTitleInFinalAnswer) {
            nextPrompt.append("如果仍需要更多工具，继续输出CMD行；否则输出最终答复，并按 TITLE: <标题> + 空行 + 正文 输出。\n");
        } else {
            nextPrompt.append("如果仍需要更多工具，继续输出CMD行；否则直接输出最终正文，不要输出TITLE行。\n");
        }
        nextPrompt.append("最多可连续调用工具30轮；若已接近上限，优先收敛并直接回答。\n");
        nextPrompt.append("注意: 最终正文不要复述工具执行过程（如已查询/已调用），只保留结果与解释。\n");
        nextPrompt.append("注意: 最终正文不要提及或猜测座位数；并在结尾补一句与当前问题相关的简短反问。\n");

        return nextPrompt.toString();
    }
}
