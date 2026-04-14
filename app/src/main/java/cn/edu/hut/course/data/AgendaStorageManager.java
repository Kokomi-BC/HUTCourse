package cn.edu.hut.course.data;

import android.content.Context;
import android.text.TextUtils;

import cn.edu.hut.course.Agenda;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class AgendaStorageManager {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private AgendaStorageManager() {
    }

    public static synchronized long createAgenda(Context context, Agenda agenda) {
        if (context == null || agenda == null) {
            return -1L;
        }
        Agenda normalized = normalizeForPersist(agenda.copy(), true);
        long id = AgendaSQLiteStore.insertAgenda(context, normalized);
        normalized.id = id;
        return id;
    }

    public static synchronized boolean updateAgenda(Context context, Agenda agenda) {
        if (context == null || agenda == null || agenda.id <= 0) {
            return false;
        }
        Agenda normalized = normalizeForPersist(agenda.copy(), false);
        return AgendaSQLiteStore.updateAgenda(context, normalized);
    }

    public static synchronized boolean deleteAgenda(Context context, long agendaId) {
        return AgendaSQLiteStore.deleteAgendaById(context, agendaId);
    }

    public static synchronized Agenda getAgenda(Context context, long agendaId) {
        return AgendaSQLiteStore.readAgendaById(context, agendaId);
    }

    public static synchronized List<Agenda> loadAllAgendas(Context context) {
        return AgendaSQLiteStore.readAllAgendas(context);
    }

    public static synchronized List<Agenda> queryAgendasByDate(Context context, String dateStr) {
        Calendar target = parseDate(dateStr);
        if (target == null) {
            return new ArrayList<>();
        }
        return queryAgendasByDate(context, target);
    }

    public static synchronized List<Agenda> queryAgendasByDate(Context context, Calendar targetDate) {
        List<Agenda> all = loadAllAgendas(context);
        List<Agenda> result = new ArrayList<>();
        if (targetDate == null) {
            return result;
        }

        Calendar target = trimToDay(targetDate);
        for (Agenda agenda : all) {
            if (agenda == null) {
                continue;
            }
            if (occursOnDate(agenda, target)) {
                result.add(agenda.copy());
            }
        }

        result.sort((a, b) -> {
            int byStart = Integer.compare(a.startMinute, b.startMinute);
            if (byStart != 0) {
                return byStart;
            }
            int byPriority = Integer.compare(b.priority, a.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return safe(a.title).compareToIgnoreCase(safe(b.title));
        });
        return result;
    }

    public static synchronized List<Agenda> searchAgendas(Context context, String keyword) {
        String key = safe(keyword).trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return new ArrayList<>();
        }

        List<Agenda> all = loadAllAgendas(context);
        List<Agenda> result = new ArrayList<>();
        for (Agenda agenda : all) {
            if (agenda == null) {
                continue;
            }
            String haystack = (safe(agenda.title) + "\n" + safe(agenda.description) + "\n" + safe(agenda.location)).toLowerCase(Locale.ROOT);
            if (haystack.contains(key)) {
                result.add(agenda.copy());
            }
        }
        result.sort((a, b) -> Long.compare(a.id, b.id));
        return result;
    }

    public static synchronized String formatDate(Calendar date) {
        if (date == null) {
            return "";
        }
        Calendar target = trimToDay(date);
        return String.format(Locale.getDefault(), "%04d-%02d-%02d",
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH) + 1,
                target.get(Calendar.DAY_OF_MONTH));
    }

    public static synchronized Calendar parseDateOrNull(String dateStr) {
        return parseDate(dateStr);
    }

    public static synchronized boolean occursOnDate(Agenda agenda, Calendar targetDate) {
        if (agenda == null || targetDate == null) {
            return false;
        }
        Calendar anchor = parseDate(agenda.date);
        if (anchor == null) {
            return false;
        }

        Calendar target = trimToDay(targetDate);
        if (target.before(anchor)) {
            return false;
        }

        String repeat = safe(agenda.repeatRule).toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(repeat) || Agenda.REPEAT_NONE.equals(repeat)) {
            return isSameDay(anchor, target);
        }
        if (Agenda.REPEAT_DAILY.equals(repeat)) {
            return !target.before(anchor);
        }
        if (Agenda.REPEAT_WEEKLY.equals(repeat)) {
            long diffDays = (target.getTimeInMillis() - anchor.getTimeInMillis()) / DAY_MS;
            return diffDays % 7 == 0;
        }
        if (Agenda.REPEAT_MONTHLY.equals(repeat)) {
            int anchorYear = anchor.get(Calendar.YEAR);
            int anchorMonth = anchor.get(Calendar.MONTH);
            int targetYear = target.get(Calendar.YEAR);
            int targetMonth = target.get(Calendar.MONTH);
            if (targetYear < anchorYear || (targetYear == anchorYear && targetMonth < anchorMonth)) {
                return false;
            }

            int anchorDay = anchor.get(Calendar.DAY_OF_MONTH);
            int maxDay = target.getActualMaximum(Calendar.DAY_OF_MONTH);
            String strategy = safe(agenda.monthlyStrategy).toLowerCase(Locale.ROOT);
            if (!Agenda.MONTHLY_MONTH_END.equals(strategy)) {
                strategy = Agenda.MONTHLY_SKIP;
            }

            int expectedDay;
            if (anchorDay <= maxDay) {
                expectedDay = anchorDay;
            } else if (Agenda.MONTHLY_MONTH_END.equals(strategy)) {
                expectedDay = maxDay;
            } else {
                return false;
            }
            return target.get(Calendar.DAY_OF_MONTH) == expectedDay;
        }
        return false;
    }

    private static Agenda normalizeForPersist(Agenda agenda, boolean forInsert) {
        agenda.title = safe(agenda.title).trim();
        agenda.description = safe(agenda.description).trim();
        agenda.location = safe(agenda.location).trim();
        while (agenda.location.startsWith("@") || agenda.location.startsWith("＠")) {
            agenda.location = agenda.location.substring(1).trim();
        }

        Calendar date = parseDate(agenda.date);
        if (date == null) {
            date = trimToDay(Calendar.getInstance());
        }
        agenda.date = formatDate(date);

        if (agenda.startMinute < 0) {
            agenda.startMinute = 0;
        }
        if (agenda.endMinute > 24 * 60) {
            agenda.endMinute = 24 * 60;
        }
        if (agenda.endMinute <= agenda.startMinute) {
            agenda.endMinute = Math.min(24 * 60, agenda.startMinute + 30);
        }

        if (agenda.priority < Agenda.PRIORITY_LOW || agenda.priority > Agenda.PRIORITY_HIGH) {
            agenda.priority = Agenda.PRIORITY_LOW;
        }

        String repeat = safe(agenda.repeatRule).toLowerCase(Locale.ROOT);
        if (!Agenda.REPEAT_DAILY.equals(repeat)
                && !Agenda.REPEAT_WEEKLY.equals(repeat)
                && !Agenda.REPEAT_MONTHLY.equals(repeat)) {
            repeat = Agenda.REPEAT_NONE;
        }
        agenda.repeatRule = repeat;

        String monthlyStrategy = safe(agenda.monthlyStrategy).toLowerCase(Locale.ROOT);
        if (!Agenda.MONTHLY_MONTH_END.equals(monthlyStrategy)) {
            monthlyStrategy = Agenda.MONTHLY_SKIP;
        }
        agenda.monthlyStrategy = monthlyStrategy;

        long now = System.currentTimeMillis();
        if (forInsert && agenda.createdAt <= 0) {
            agenda.createdAt = now;
        }
        agenda.updatedAt = now;
        return agenda;
    }

    private static Calendar parseDate(String dateStr) {
        String input = safe(dateStr).trim();
        if (input.isEmpty()) {
            return null;
        }
        String[] parts = input.split("-");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            Calendar cal = Calendar.getInstance();
            cal.setLenient(false);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.getTimeInMillis();
            return cal;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Calendar trimToDay(Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
