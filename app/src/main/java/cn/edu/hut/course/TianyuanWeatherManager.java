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
    private static final String WEATHER_1D_URL = "https://www.weather.com.cn/weather1d/101250309.shtml";
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
        public final String feelsLike;            // e.g. "31" or empty

        DayForecast(String date, String dayLabel, String weather, String temperature,
                    String wind, String humidity, String feelsLike) {
            this.date = date;
            this.dayLabel = dayLabel;
            this.weather = weather;
            this.temperature = temperature;
            this.wind = wind;
            this.humidity = humidity;
            this.feelsLike = feelsLike;
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
            forecasts.add(new DayForecast(e.date, label, e.weather, e.temperature, e.wind,
                    e.humidity, e.feelsLike));
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
                WeatherSnapshot adjusted = applyEveningHighLowPolicy(context, fetched);
                saveToDb(context, adjusted);
                return adjusted;
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
        
        // 获取1D页面的当前温度和湿度
        String currentTemp = "";
        String currentHumidity = "";
        try {
            Document doc1d = Jsoup.connect(WEATHER_1D_URL)
                    .userAgent("Mozilla/5.0")
                    .timeout(12000)
                    .get();
            Elements scripts = doc1d.select("script");
            for (Element script : scripts) {
                String html = script.html();
                if (html.contains("var observe24h_data =")) {
                    int start = html.indexOf("var observe24h_data =") + "var observe24h_data =".length();
                    int end = html.indexOf(";", start);
                    if (start > 0 && end > start) {
                        String jsonStr = html.substring(start, end).trim();
                        org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                        org.json.JSONArray od2 = obj.optJSONObject("od").optJSONArray("od2");
                        if (od2 != null && od2.length() > 0) {
                            org.json.JSONObject latest = od2.getJSONObject(0);
                            currentTemp = latest.optString("od22").replace(".0", "");
                            if (currentTemp.contains(".")) {
                                currentTemp = String.valueOf(Math.round(Float.parseFloat(currentTemp)));
                            }
                            currentHumidity = latest.optString("od27");
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            String humidity = i == 0 ? currentHumidity : "";
            String dayFeelsLike = i == 0 ? currentTemp : ""; // Using feelsLike field to store current real-time temp
            forecasts.add(new DayForecast(dateStr, dayLabel, weather, temperature, wind,
                    humidity, dayFeelsLike));
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
                    f.date, f.weather, f.temperature, f.wind, f.humidity, f.feelsLike, 0L));
        }
        WeatherSQLiteStore.upsertDays(context, entries);
    }

    private static WeatherSnapshot applyEveningHighLowPolicy(@NonNull Context context,
                                                            @NonNull WeatherSnapshot snapshot) {
        if (!shouldSkipHighLow(snapshot.updateTime)) return snapshot;
        String today = WeatherSQLiteStore.DateUtils.todayDate();
        WeatherSQLiteStore.DailyEntry existing = WeatherSQLiteStore.queryByDate(context, today);
        if (existing == null || TextUtils.isEmpty(existing.temperature)) return snapshot;

        List<DayForecast> adjusted = new ArrayList<>();
        for (DayForecast f : snapshot.forecasts) {
            if (f.date != null && f.date.equals(today)) {
                String feelsLike = TextUtils.isEmpty(f.feelsLike) ? existing.feelsLike : f.feelsLike;
                adjusted.add(new DayForecast(f.date, f.dayLabel, f.weather,
                        existing.temperature, f.wind, f.humidity, feelsLike));
            } else {
                adjusted.add(f);
            }
        }
        return new WeatherSnapshot(snapshot.success, snapshot.fromCache, snapshot.area,
                snapshot.updateTime, snapshot.message, adjusted);
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

    private static boolean shouldSkipHighLow(@Nullable String updateTime) {
        if (TextUtils.isEmpty(updateTime)) return false;
        String normalized = updateTime.replace('：', ':');
        return normalized.contains("18:00")
                || normalized.contains("18时")
                || normalized.contains("18点");
    }

    @NonNull
    private static String extractFeelsLike(@NonNull Document doc) {
        try {
            String text = firstTextBySelectors(doc,
                    new String[]{"div.sk", "div#sk", "div#now", "div.t", "div.today", "div.left"});
            String temp = extractDigitsAfterKeywords(text,
                    new String[]{"体感温度", "体感", "温度", "气温"});
            if (!TextUtils.isEmpty(temp)) return temp;

            String html = doc.html();
            temp = extractDigitsAfterKeywords(html, new String[]{"\"temp\""});
            if (!TextUtils.isEmpty(temp)) return temp;
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Try to extract today's humidity from the page. Returns "" if not found.
     * Searches the live-weather section then falls back to full body text.
     */
    @NonNull
    private static String extractHumidity(@NonNull Document doc, int dayIndex) {
        if (dayIndex != 0) return ""; // only today has humidity on 7d page
        try {
            String text = firstTextBySelectors(doc,
                    new String[]{"div.t", "div.sk", "div.today", "div.left"});
            String humidity = extractDigitsAfterKeywords(text,
                    new String[]{"湿度", "相对湿度"});
            if (!TextUtils.isEmpty(humidity)) return humidity;

            String html = doc.html();
            humidity = extractDigitsAfterKeywords(html,
                    new String[]{"\"humidity\"", "humidity", "\"shidu\"", "shidu"});
            if (!TextUtils.isEmpty(humidity)) return humidity;
        } catch (Exception ignored) {
        }
        return "";
    }

    @NonNull
    private static String firstTextBySelectors(@NonNull Document doc, @NonNull String[] selectors) {
        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (!els.isEmpty()) {
                return els.text();
            }
        }
        return doc.body() == null ? "" : doc.body().text();
    }

    @NonNull
    private static String extractDigitsAfterKeywords(@NonNull String text,
                                                     @NonNull String[] keywords) {
        if (TextUtils.isEmpty(text)) return "";
        for (String keyword : keywords) {
            String digits = extractDigitsAfterKeyword(text, keyword);
            if (!TextUtils.isEmpty(digits)) return digits;
        }
        return "";
    }

    @NonNull
    private static String extractDigitsAfterKeyword(@NonNull String text, @NonNull String keyword) {
        int idx = text.indexOf(keyword);
        while (idx >= 0) {
            int start = idx + keyword.length();
            String digits = scanDigits(text, start, 12);
            if (!TextUtils.isEmpty(digits)) return digits;
            idx = text.indexOf(keyword, idx + keyword.length());
        }
        return "";
    }

    @NonNull
    private static String scanDigits(@NonNull String text, int start, int maxSkip) {
        int i = start;
        int skipped = 0;
        while (i < text.length() && skipped < maxSkip && !Character.isDigit(text.charAt(i))) {
            i++;
            skipped++;
        }
        StringBuilder digits = new StringBuilder();
        while (i < text.length() && Character.isDigit(text.charAt(i)) && digits.length() < 3) {
            digits.append(text.charAt(i));
            i++;
        }
        return digits.toString();
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
