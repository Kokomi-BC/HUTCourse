package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;

public final class AiConfigStore {

    private static final String PREF_AI = "ai_config";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    public static final String PROVIDER_SDK = "sdk";
    public static final String PROVIDER_CURL = "curl";

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private AiConfigStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_AI, Context.MODE_PRIVATE);
    }

    public static String getProvider(Context context) {
        return prefs(context).getString(KEY_PROVIDER, PROVIDER_SDK);
    }

    public static String getBaseUrl(Context context) {
        return prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public static String getApiKey(Context context) {
        return prefs(context).getString(KEY_API_KEY, "");
    }

    public static String getModel(Context context) {
        return prefs(context).getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public static void save(Context context, String provider, String baseUrl, String apiKey, String model) {
        prefs(context)
                .edit()
                .putString(KEY_PROVIDER, provider)
                .putString(KEY_BASE_URL, baseUrl)
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_MODEL, model)
                .apply();
    }
}
