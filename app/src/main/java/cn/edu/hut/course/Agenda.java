package cn.edu.hut.course;

import java.io.Serializable;

public class Agenda implements Serializable {

    public static final int PRIORITY_LOW = 1;
    public static final int PRIORITY_MEDIUM = 2;
    public static final int PRIORITY_HIGH = 3;

    public static final String REPEAT_NONE = "none";
    public static final String REPEAT_DAILY = "daily";
    public static final String REPEAT_WEEKLY = "weekly";
    public static final String REPEAT_MONTHLY = "monthly";

    public static final String MONTHLY_SKIP = "skip";
    public static final String MONTHLY_MONTH_END = "month_end";

    public long id;
    public String title = "";
    public String description = "";
    public String location = "";
    public String date = ""; // yyyy-MM-dd
    public int startMinute = 8 * 60;
    public int endMinute = 9 * 60;
    public int priority = PRIORITY_LOW;
    public String repeatRule = REPEAT_NONE;
    public String monthlyStrategy = MONTHLY_SKIP;
    public long createdAt;
    public long updatedAt;

    public Agenda copy() {
        Agenda agenda = new Agenda();
        agenda.id = id;
        agenda.title = title;
        agenda.description = description;
        agenda.location = location;
        agenda.date = date;
        agenda.startMinute = startMinute;
        agenda.endMinute = endMinute;
        agenda.priority = priority;
        agenda.repeatRule = repeatRule;
        agenda.monthlyStrategy = monthlyStrategy;
        agenda.createdAt = createdAt;
        agenda.updatedAt = updatedAt;
        return agenda;
    }
}
