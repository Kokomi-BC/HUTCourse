package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AiConfigStore {

    private static final String PREF_AI = "ai_config";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_MODELS_JSON = "models_json";
    private static final String KEY_SKILL_ENABLED = "skill_enabled";
    private static final String KEY_MEMORY_SKILL_ENABLED = "memory_skill_enabled";
    private static final String KEY_COURSE_SKILL_ENABLED = "course_skill_enabled";
    private static final String KEY_NAVIGATION_SKILL_ENABLED = "navigation_skill_enabled";
    private static final String KEY_CLASSROOM_SKILL_ENABLED = "classroom_skill_enabled";
    private static final String KEY_AGENDA_SKILL_ENABLED = "agenda_skill_enabled";
    private static final String KEY_WEB_SEARCH_SKILL_ENABLED = "web_search_skill_enabled";

    public static final String SKILL_MEMORY = "memory";
    public static final String SKILL_COURSE = "course";
    public static final String SKILL_NAVIGATION = "navigation";
    public static final String SKILL_CLASSROOM = "classroom";
    public static final String SKILL_AGENDA = "agenda";
    public static final String SKILL_SEARCH = "search";
    public static final String SKILL_WEB_SEARCH = "web_search";

    public static final String PROVIDER_SDK = "sdk";
    public static final String PROVIDER_CURL = "curl";

    private static final String DEFAULT_BASE_URL = "";
    private static final String DEFAULT_MODEL = "";

    public static final class AiModelConfig {
        public String id;
        public String displayName;
        public String modelName;
        public String provider;
        public String baseUrl;
        public String apiKey;
        public boolean multimodal;
        public int priority;

        public AiModelConfig copy() {
            AiModelConfig one = new AiModelConfig();
            one.id = id;
            one.displayName = displayName;
            one.modelName = modelName;
            one.provider = provider;
            one.baseUrl = baseUrl;
            one.apiKey = apiKey;
            one.multimodal = multimodal;
            one.priority = priority;
            return one;
        }
    }

    private AiConfigStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_AI, Context.MODE_PRIVATE);
    }

    private static JSONObject toJson(AiModelConfig model) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("id", safe(model.id));
        obj.put("displayName", safe(model.displayName));
        obj.put("modelName", safe(model.modelName));
        obj.put("provider", safe(normalizeProvider(model.provider)));
        obj.put("baseUrl", safe(model.baseUrl));
        obj.put("apiKey", safe(model.apiKey));
        obj.put("multimodal", model.multimodal);
        obj.put("priority", model.priority);
        return obj;
    }

    private static AiModelConfig fromJson(JSONObject obj, int fallbackPriority) {
        AiModelConfig model = new AiModelConfig();
        model.id = obj == null ? "" : obj.optString("id", "");
        model.displayName = obj == null ? "" : obj.optString("displayName", "");
        model.modelName = obj == null ? "" : obj.optString("modelName", "");
        model.provider = obj == null ? PROVIDER_SDK : normalizeProvider(obj.optString("provider", PROVIDER_SDK));
        model.baseUrl = obj == null ? "" : obj.optString("baseUrl", "");
        model.apiKey = obj == null ? "" : obj.optString("apiKey", "");
        model.multimodal = obj != null && obj.optBoolean("multimodal", false);
        model.priority = obj == null ? fallbackPriority : obj.optInt("priority", fallbackPriority);
        ensureModelIdentity(model, fallbackPriority);
        return model;
    }

    private static void ensureModelIdentity(AiModelConfig model, int fallbackPriority) {
        if (model == null) {
            return;
        }
        if (TextUtils.isEmpty(model.id)) {
            model.id = UUID.randomUUID().toString();
        }
        model.displayName = safe(model.displayName);
        model.modelName = safe(model.modelName);
        if (TextUtils.isEmpty(model.provider)) {
            model.provider = PROVIDER_SDK;
        }
        if (fallbackPriority >= 0 && model.priority < 0) {
            model.priority = fallbackPriority;
        }
    }

    private static String normalizeProvider(String provider) {
        String normalized = safe(provider).trim().toLowerCase();
        if ("openaisdk".equals(normalized)) {
            return PROVIDER_SDK;
        }
        if (PROVIDER_CURL.equals(normalized)) {
            return PROVIDER_CURL;
        }
        return PROVIDER_SDK;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void normalizePriorities(List<AiModelConfig> models) {
        if (models == null) {
            return;
        }
        for (int i = 0; i < models.size(); i++) {
            AiModelConfig one = models.get(i);
            ensureModelIdentity(one, i);
            one.priority = i;
        }
    }

    private static List<AiModelConfig> sanitizeModels(List<AiModelConfig> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<AiModelConfig> out = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            AiModelConfig one = raw.get(i);
            if (one == null) {
                continue;
            }
            ensureModelIdentity(one, i);
            if (seenIds.contains(one.id)) {
                one.id = UUID.randomUUID().toString();
            }
            seenIds.add(one.id);
            out.add(one.copy());
        }
        normalizePriorities(out);
        return out;
    }

    private static void syncLegacyFromTopModel(Context context, List<AiModelConfig> models) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (models == null || models.isEmpty()) {
            editor.remove(KEY_PROVIDER)
                    .remove(KEY_BASE_URL)
                    .remove(KEY_API_KEY)
                    .remove(KEY_MODEL)
                    .apply();
            return;
        }
        AiModelConfig top = models.get(0);
        editor.putString(KEY_PROVIDER, safe(normalizeProvider(top.provider)))
                .putString(KEY_BASE_URL, safe(top.baseUrl))
                .putString(KEY_API_KEY, safe(top.apiKey))
                .putString(KEY_MODEL, safe(top.modelName))
                .apply();
    }

    private static void persistModels(Context context, List<AiModelConfig> models) {
        List<AiModelConfig> sanitized = sanitizeModels(models);
        JSONArray arr = new JSONArray();
        for (AiModelConfig one : sanitized) {
            try {
                arr.put(toJson(one));
            } catch (Exception ignored) {
            }
        }
        prefs(context)
                .edit()
                .putString(KEY_MODELS_JSON, arr.toString())
                .apply();
        syncLegacyFromTopModel(context, sanitized);
    }

    private static List<AiModelConfig> readModels(Context context) {
        String raw = prefs(context).getString(KEY_MODELS_JSON, "");
        if (TextUtils.isEmpty(raw)) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = new JSONArray(raw);
            List<AiModelConfig> models = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                AiModelConfig one = fromJson(obj, i);
                models.add(one);
            }
            models.sort(Comparator.comparingInt(a -> a.priority));
            return sanitizeModels(models);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void ensureMigrated(Context context) {
        SharedPreferences preferences = prefs(context);
        String modelsRaw = preferences.getString(KEY_MODELS_JSON, "");
        if (!TextUtils.isEmpty(modelsRaw)) {
            List<AiModelConfig> existing = readModels(context);
            if (!existing.isEmpty()) {
                syncLegacyFromTopModel(context, existing);
            }
            return;
        }

        String legacyProvider = normalizeProvider(preferences.getString(KEY_PROVIDER, PROVIDER_SDK));
        String legacyBaseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        String legacyApiKey = preferences.getString(KEY_API_KEY, "");
        String legacyModel = preferences.getString(KEY_MODEL, DEFAULT_MODEL);
        boolean hasLegacy = !TextUtils.isEmpty(legacyModel)
                || !TextUtils.isEmpty(legacyApiKey)
                || !TextUtils.isEmpty(legacyBaseUrl);
        if (!hasLegacy) {
            return;
        }

        AiModelConfig model = new AiModelConfig();
        model.id = UUID.randomUUID().toString();
        model.displayName = "";
        model.modelName = safe(legacyModel);
        model.provider = legacyProvider;
        model.baseUrl = safe(legacyBaseUrl);
        model.apiKey = safe(legacyApiKey);
        model.multimodal = false;
        model.priority = 0;
        persistModels(context, Collections.singletonList(model));
    }

    public static synchronized List<AiModelConfig> getModels(Context context) {
        ensureMigrated(context);
        return readModels(context);
    }

    public static synchronized void saveModels(Context context, List<AiModelConfig> models) {
        persistModels(context, models);
    }

    public static synchronized void upsertModel(Context context, AiModelConfig model) {
        if (model == null) {
            return;
        }
        List<AiModelConfig> models = getModels(context);
        ensureModelIdentity(model, models.size());
        int target = -1;
        for (int i = 0; i < models.size(); i++) {
            if (safe(models.get(i).id).equals(safe(model.id))) {
                target = i;
                break;
            }
        }
        if (target >= 0) {
            AiModelConfig existing = models.get(target);
            model.priority = existing.priority;
            models.set(target, model.copy());
        } else {
            model.priority = models.size();
            models.add(model.copy());
        }
        persistModels(context, models);
    }

    public static synchronized void deleteModel(Context context, String modelId) {
        List<AiModelConfig> models = getModels(context);
        if (models.isEmpty()) {
            return;
        }
        models.removeIf(item -> safe(item.id).equals(safe(modelId)));
        persistModels(context, models);
    }

    public static synchronized void reorderByIds(Context context, List<String> orderedIds) {
        List<AiModelConfig> models = getModels(context);
        if (models.size() <= 1) {
            return;
        }
        if (orderedIds == null || orderedIds.isEmpty()) {
            persistModels(context, models);
            return;
        }
        List<AiModelConfig> sorted = new ArrayList<>();
        for (String id : orderedIds) {
            for (AiModelConfig one : models) {
                if (safe(one.id).equals(safe(id))) {
                    sorted.add(one.copy());
                    break;
                }
            }
        }
        for (AiModelConfig one : models) {
            boolean exists = false;
            for (AiModelConfig inSorted : sorted) {
                if (safe(inSorted.id).equals(safe(one.id))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                sorted.add(one.copy());
            }
        }
        persistModels(context, sorted);
    }

    public static synchronized AiModelConfig getTopPriorityModel(Context context) {
        List<AiModelConfig> models = getModels(context);
        if (models.isEmpty()) {
            return null;
        }
        return models.get(0).copy();
    }

    public static synchronized AiModelConfig findModelById(Context context, String modelId) {
        List<AiModelConfig> models = getModels(context);
        for (AiModelConfig one : models) {
            if (safe(one.id).equals(safe(modelId))) {
                return one.copy();
            }
        }
        return null;
    }

    public static String getProvider(Context context) {
        AiModelConfig top = getTopPriorityModel(context);
        if (top != null) {
            return normalizeProvider(top.provider);
        }
        return normalizeProvider(prefs(context).getString(KEY_PROVIDER, PROVIDER_SDK));
    }

    public static String getBaseUrl(Context context) {
        AiModelConfig top = getTopPriorityModel(context);
        if (top != null) {
            return safe(top.baseUrl);
        }
        return prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public static String getApiKey(Context context) {
        AiModelConfig top = getTopPriorityModel(context);
        if (top != null) {
            return safe(top.apiKey);
        }
        return prefs(context).getString(KEY_API_KEY, "");
    }

    public static String getModel(Context context) {
        AiModelConfig top = getTopPriorityModel(context);
        if (top != null) {
            return safe(top.modelName);
        }
        return prefs(context).getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public static void save(Context context, String provider, String baseUrl, String apiKey, String model) {
        List<AiModelConfig> models = getModels(context);
        AiModelConfig target;
        if (models.isEmpty()) {
            target = new AiModelConfig();
            target.id = UUID.randomUUID().toString();
            target.priority = 0;
        } else {
            target = models.get(0);
        }
        target.modelName = safe(model);
        target.provider = normalizeProvider(provider);
        target.baseUrl = safe(baseUrl);
        target.apiKey = safe(apiKey);
        persistModels(context, Collections.singletonList(target));
    }

    public static boolean isSkillEnabled(Context context) {
        return true;
    }

    public static void setSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .remove(KEY_SKILL_ENABLED)
                .apply();
    }

    public static boolean isWebSearchSkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_WEB_SEARCH_SKILL_ENABLED, false);
    }

    public static void setWebSearchSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_WEB_SEARCH_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isMemorySkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MEMORY_SKILL_ENABLED, true);
    }

    public static void setMemorySkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_MEMORY_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isCourseSkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_COURSE_SKILL_ENABLED, true);
    }

    public static void setCourseSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_COURSE_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isNavigationSkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NAVIGATION_SKILL_ENABLED, true);
    }

    public static void setNavigationSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_NAVIGATION_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isClassroomSkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CLASSROOM_SKILL_ENABLED, true);
    }

    public static void setClassroomSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_CLASSROOM_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isAgendaSkillEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AGENDA_SKILL_ENABLED, true);
    }

    public static void setAgendaSkillEnabled(Context context, boolean enabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_AGENDA_SKILL_ENABLED, enabled)
                .apply();
    }

    public static boolean isSkillEnabledByName(Context context, String skillName) {
        String normalized = safe(skillName).trim().toLowerCase();
        switch (normalized) {
            case SKILL_MEMORY:
                return isMemorySkillEnabled(context);
            case SKILL_COURSE:
                return isCourseSkillEnabled(context);
            case SKILL_NAVIGATION:
                return isNavigationSkillEnabled(context);
            case SKILL_CLASSROOM:
                return isClassroomSkillEnabled(context);
            case SKILL_AGENDA:
                return isAgendaSkillEnabled(context);
            case SKILL_SEARCH:
            case SKILL_WEB_SEARCH:
                return isWebSearchSkillEnabled(context);
            default:
                return true;
        }
    }
}
