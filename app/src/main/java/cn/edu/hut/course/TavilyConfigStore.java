package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;

public final class TavilyConfigStore {

    private static final String PREF_TAVILY = "tavily_config";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "https://api.tavily.com/search";

    private TavilyConfigStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_TAVILY, Context.MODE_PRIVATE);
    }

    public static String getApiKey(Context context) {
        return safe(prefs(context).getString(KEY_API_KEY, ""));
    }

    public static String getBaseUrl(Context context) {
        String baseUrl = safe(prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL)).trim();
        return baseUrl.isEmpty() ? DEFAULT_BASE_URL : baseUrl;
    }

    public static void save(Context context, String apiKey, String baseUrl) {
        prefs(context)
                .edit()
                .putString(KEY_API_KEY, safe(apiKey).trim())
                .putString(KEY_BASE_URL, safe(baseUrl).trim())
                .apply();
    }

    public static boolean isConfigured(Context context) {
        return !getApiKey(context).trim().isEmpty();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
