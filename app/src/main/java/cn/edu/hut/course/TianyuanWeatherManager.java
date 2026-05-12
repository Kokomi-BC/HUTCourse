package cn.edu.hut.course;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import cn.edu.hut.course.data.WeatherSQLiteStore;

public final class TianyuanWeatherManager {

    private static final String WEATHER_URL = "https://www.weather.com.cn/weather/101250309.shtml";
    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000L; // 30 minutes

    private TianyuanWeatherManager() {
    }

    public interface Callback {
        void onResult(@NonNull WeatherSnapshot snapshot);
    }

    public static final class DayForecast {
        @Nullable public final String date;       // "YYYY-MM-DD", null if unknown
        public final String dayLabel;
        public final String weather;
        public final String temperature;
        public final String wind;
        public final String humidity;             // e.g. "65" or empty

        DayForecast(String date, String dayLabel, String weather, String temperature, String wind, String humidity) {
            this.date = date;
            this.dayLabel = dayLabel;
            this.weather = weather;
            this.temperature = temperature;
            this.wind = wind;
            this.humidity = humidity;
        }
    }

    public static final class WeatherSnapshot {
        public final boolean success;
        public final boolean fromCache;
        public final String area;
        public final String updateTime;
        public final String message;
        public final List<DayForecast> forecasts;

        WeatherSnapshot(boolean success,
                        boolean fromCache,
                        String area,
                        String updateTime,
                        String message,
                        List<DayForecast> forecasts) {
            this.success = success;
            this.fromCache = fromCache;
            this.area = area;
            this.updateTime = updateTime;
            this.message = message;
            this.forecasts = forecasts == null ? new ArrayList<>() : forecasts;
        }
    }

    /**
     * Whether a network refresh should be attempted now.
     * Returns true only on a "cold start" scenario (no fetch time recorded,
     * or the last fetch was more than REFRESH_INTERVAL_MS ago).
     */
    public static boolean shouldRefreshNow(@NonNull Context context) {
        long lastFetch = WeatherSQLiteStore.getLastFetchTime(context);
        if (lastFetch == 0L) return true;
        return (System.currentTimeMillis() - lastFetch) > REFRESH_INTERVAL_MS;
    }

    /**
     * Request weather data. If forceRefresh is true, always fetches from web.
     * Otherwise uses DB if within the refresh interval; else fetches from web
     * and writes results into the DB store.
     */
    public static void requestWeather(@NonNull Context context,
                                      boolean forceRefresh,
                                      @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            WeatherSnapshot snapshot = loadWeatherInternal(appContext, forceRefresh);
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(snapshot));
        }, "tianyuan-weather").start();
    }

    /**
     * Build a WeatherSnapshot from the DB store for the next 7 days (today .. today+6).
     * Returns null if there is no data at all for today.
     */
    @Nullable
    public static WeatherSnapshot buildFromDb(@NonNull Context context) {
        String today = WeatherSQLiteStore.DateUtils.todayDate();
        String day7 = WeatherSQLiteStore.DateUtils.daysFromNowDate(6);
        List<WeatherSQLiteStore.DailyEntry> entries =
                WeatherSQLiteStore.queryRange(context, today, day7);

        if (entries.isEmpty()) return null;

        List<DayForecast> forecasts = new ArrayList<>();
        for (WeatherSQLiteStore.DailyEntry e : entries) {
            String label = resolveDayLabelForDate(e.date);
            forecasts.add(new DayForecast(e.date, label, e.weather, e.temperature, e.wind, e.humidity));
        }

        return new WeatherSnapshot(true, true, "株洲·天元区", "", "", forecasts);
    }

    /**
     * Check whether the DB contains any weather data before today.
     */
    public static boolean hasDataBeforeToday(@NonNull Context context) {
        return WeatherSQLiteStore.hasDataBeforeToday(context);
    }

    // ---- internal ----

    private static WeatherSnapshot loadWeatherInternal(Context context, boolean forceRefresh) {
        // If not forcing, check DB first
        if (!forceRefresh) {
            WeatherSnapshot fromDb = buildFromDb(context);
            if (fromDb != null && !shouldRefreshNow(context)) {
                return fromDb;
            }
        }

        try {
            WeatherSnapshot fetched = fetchFromWeb();
            if (fetched.success) {
                saveToDb(context, fetched);
                return fetched;
            }
            // Fallback to DB
            WeatherSnapshot fromDb = buildFromDb(context);
            if (fromDb != null) {
                return new WeatherSnapshot(true, true, fromDb.area, fromDb.updateTime,
                        fetched.message, fromDb.forecasts);
            }
            return fetched;
        } catch (Exception e) {
            String reason = safe(e.getMessage()).trim();
            if (reason.isEmpty()) {
                reason = "天气抓取异常";
            }
            WeatherSnapshot fromDb = buildFromDb(context);
            if (fromDb != null) {
                return new WeatherSnapshot(true, true, fromDb.area, fromDb.updateTime,
                        reason, fromDb.forecasts);
            }
            return new WeatherSnapshot(false, false, "株洲·天元区", "", reason, new ArrayList<>());
        }
    }

    private static WeatherSnapshot fetchFromWeb() throws Exception {
        Document doc = Jsoup.connect(WEATHER_URL)
                .userAgent("Mozilla/5.0")
                .timeout(12000)
                .get();

        String pageText = safe(doc.text());
        if (!pageText.contains("株洲") || !pageText.contains("天元")) {
            throw new IllegalStateException("天气源校验失败：非株洲天元区");
        }

        String area = "天元区";
        String updateTime = extractUpdateTime(pageText);

        LocalDate today = LocalDate.now();
        Elements dayNodes = doc.select("div#7d ul.t.clearfix li");
        List<DayForecast> forecasts = new ArrayList<>();
        for (int i = 0; i < dayNodes.size() && forecasts.size() < 7; i++) {
            Element node = dayNodes.get(i);
            String dayLabel = safe(node.selectFirst("h1") == null ? "" : node.selectFirst("h1").text()).trim();
            String weather = safe(node.selectFirst("p.wea") == null ? "" : node.selectFirst("p.wea").text()).trim();

            Element temNode = node.selectFirst("p.tem");
            String maxTemp = "";
            String minTemp = "";
            if (temNode != null) {
                Element max = temNode.selectFirst("span");
                Element min = temNode.selectFirst("i");
                maxTemp = safe(max == null ? "" : max.text()).trim();
                minTemp = safe(min == null ? "" : min.text()).trim();
            }
            String temperature;
            if (!maxTemp.isEmpty() && !minTemp.isEmpty()) {
                temperature = maxTemp + "/" + minTemp;
            } else if (!maxTemp.isEmpty()) {
                temperature = maxTemp;
            } else {
                temperature = minTemp;
            }

            Element windNode = node.selectFirst("p.win i");
            String wind = safe(windNode == null ? "" : windNode.text()).trim();

            if (dayLabel.isEmpty() && weather.isEmpty() && temperature.isEmpty()) {
                continue;
            }

            String dateStr = today.plusDays(i).toString();
            String humidity = extractHumidity(doc, i);
            forecasts.add(new DayForecast(dateStr, dayLabel, weather, temperature, wind, humidity));
        }

        if (forecasts.isEmpty()) {
            throw new IllegalStateException("天气解析失败：未提取到预报数据");
        }

        return new WeatherSnapshot(true, false, area, updateTime, "", forecasts);
    }

    private static void saveToDb(Context context, WeatherSnapshot snapshot) {
        List<WeatherSQLiteStore.DailyEntry> entries = new ArrayList<>();
        for (DayForecast f : snapshot.forecasts) {
            if (f.date == null) continue;
            entries.add(new WeatherSQLiteStore.DailyEntry(
                    f.date, f.weather, f.temperature, f.wind, f.humidity, 0L));
        }
        WeatherSQLiteStore.upsertDays(context, entries);
    }

    private static String extractUpdateTime(String pageText) {
        if (TextUtils.isEmpty(pageText)) {
            return "";
        }
        int index = pageText.indexOf("更新");
        if (index <= 0) {
            return "";
        }
        int start = Math.max(0, index - 16);
        String fragment = pageText.substring(start, index + 2);
        fragment = fragment.replace('\n', ' ').trim();
        return fragment;
    }

    /**
     * Try to extract today's humidity from the page. Returns "" if not found.
     * Searches the live-weather section then falls back to full body text.
     */
    @NonNull
    private static String extractHumidity(@NonNull Document doc, int dayIndex) {
        if (dayIndex != 0) return ""; // only today has humidity on 7d page
        try {
            // Try various known container selectors, then fall back to body text
            String text = "";
            for (String sel : new String[]{"div.t", "div.sk", "div.today", "div.left"}) {
                Elements els = doc.select(sel);
                if (!els.isEmpty()) {
                    text = els.text();
                    break;
                }
            }
            if (TextUtils.isEmpty(text)) {
                text = doc.body().text();
            }
            // Try "湿度" first, then "相对湿度"
            for (String keyword : new String[]{"湿度", "相对湿度"}) {
                int idx = text.indexOf(keyword);
                if (idx >= 0) {
                    int start = idx + keyword.length();
                    StringBuilder digits = new StringBuilder();
                    while (start < text.length() && Character.isDigit(text.charAt(start))) {
                        digits.append(text.charAt(start));
                        start++;
                    }
                    if (digits.length() > 0) {
                        return digits.toString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @NonNull
    private static String resolveDayLabelForDate(@NonNull String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now();
            long diff = date.toEpochDay() - today.toEpochDay();
            if (diff == 0) return "今天";
            if (diff == 1) return "明天";
            if (diff == 2) return "后天";
            if (diff == 3) return "大后天";
            return "第" + (diff + 1) + "天";
        } catch (Exception ignored) {
            return dateStr;
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
