package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TianyuanWeatherManager {

    private static final String PREF_WEATHER = "tianyuan_weather";
    private static final String KEY_CACHE_JSON = "cache_json";
    private static final String KEY_CACHE_DAY = "cache_day";
    private static final String KEY_CACHE_TIME = "cache_time";

    private static final String WEATHER_URL = "https://www.weather.com.cn/weather/101250309.shtml";

    private TianyuanWeatherManager() {
    }

    public interface Callback {
        void onResult(@NonNull WeatherSnapshot snapshot);
    }

    public static final class DayForecast {
        public final String dayLabel;
        public final String weather;
        public final String temperature;
        public final String wind;

        DayForecast(String dayLabel, String weather, String temperature, String wind) {
            this.dayLabel = dayLabel;
            this.weather = weather;
            this.temperature = temperature;
            this.wind = wind;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("dayLabel", safe(dayLabel));
            obj.put("weather", safe(weather));
            obj.put("temperature", safe(temperature));
            obj.put("wind", safe(wind));
            return obj;
        }

        static DayForecast fromJson(@Nullable JSONObject obj) {
            if (obj == null) {
                return null;
            }
            return new DayForecast(
                    safe(obj.optString("dayLabel", "")),
                    safe(obj.optString("weather", "")),
                    safe(obj.optString("temperature", "")),
                    safe(obj.optString("wind", ""))
            );
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

    public static void requestWeather(@NonNull Context context,
                                      boolean forceRefresh,
                                      @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            WeatherSnapshot snapshot = loadWeatherInternal(appContext, forceRefresh);
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(snapshot));
        }, "tianyuan-weather").start();
    }

    private static WeatherSnapshot loadWeatherInternal(Context context, boolean forceRefresh) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE);
        String todayKey = LocalDate.now().toString();

        WeatherSnapshot cached = readCachedSnapshot(prefs);
        String cachedDay = safe(prefs.getString(KEY_CACHE_DAY, ""));
        if (!forceRefresh && !cachedDay.isEmpty() && cachedDay.equals(todayKey) && cached != null && cached.success) {
            return new WeatherSnapshot(true, true, cached.area, cached.updateTime, "", cached.forecasts);
        }

        try {
            WeatherSnapshot fetched = fetchFromWeb();
            if (fetched.success) {
                saveCache(prefs, todayKey, fetched);
                return fetched;
            }
            if (cached != null && cached.success) {
                return new WeatherSnapshot(true, true, cached.area, cached.updateTime, fetched.message, cached.forecasts);
            }
            return fetched;
        } catch (Exception e) {
            String reason = safe(e.getMessage()).trim();
            if (reason.isEmpty()) {
                reason = "天气抓取异常";
            }
            if (cached != null && cached.success) {
                return new WeatherSnapshot(true, true, cached.area, cached.updateTime, reason, cached.forecasts);
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

        String area = "株洲·天元区";
        String updateTime = extractUpdateTime(pageText);

        Elements dayNodes = doc.select("div#7d ul.t.clearfix li");
        List<DayForecast> forecasts = new ArrayList<>();
        for (int i = 0; i < dayNodes.size() && forecasts.size() < 3; i++) {
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
            forecasts.add(new DayForecast(dayLabel, weather, temperature, wind));
        }

        if (forecasts.isEmpty()) {
            throw new IllegalStateException("天气解析失败：未提取到近三天预报");
        }

        return new WeatherSnapshot(true, false, area, updateTime, "", forecasts);
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

    private static void saveCache(SharedPreferences prefs, String dayKey, WeatherSnapshot snapshot) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("success", snapshot.success);
        obj.put("area", safe(snapshot.area));
        obj.put("updateTime", safe(snapshot.updateTime));
        JSONArray arr = new JSONArray();
        for (DayForecast one : snapshot.forecasts) {
            arr.put(one.toJson());
        }
        obj.put("forecasts", arr);

        prefs.edit()
                .putString(KEY_CACHE_DAY, dayKey)
                .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                .putString(KEY_CACHE_JSON, obj.toString())
                .apply();
    }

    @Nullable
    private static WeatherSnapshot readCachedSnapshot(SharedPreferences prefs) {
        String raw = safe(prefs.getString(KEY_CACHE_JSON, "")).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.optJSONArray("forecasts");
            List<DayForecast> forecasts = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    DayForecast one = DayForecast.fromJson(arr.optJSONObject(i));
                    if (one != null) {
                        forecasts.add(one);
                    }
                }
            }
            return new WeatherSnapshot(
                    obj.optBoolean("success", true),
                    true,
                    safe(obj.optString("area", "株洲·天元区")),
                    safe(obj.optString("updateTime", "")),
                    "",
                    forecasts
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
