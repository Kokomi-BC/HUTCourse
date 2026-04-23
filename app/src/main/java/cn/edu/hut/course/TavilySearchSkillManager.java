package cn.edu.hut.course;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TavilySearchSkillManager {

    private TavilySearchSkillManager() {
    }

    public static String search(Context context, String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) {
            return "搜索失败：关键词为空";
        }

        String apiKey = TavilyConfigStore.getApiKey(context).trim();
        if (apiKey.isEmpty()) {
            return "配置缺失：请先在 AI 设置中完成 Tavily API Key 配置";
        }

        String endpoint = TavilyConfigStore.getBaseUrl(context).trim();
        if (endpoint.isEmpty()) {
            endpoint = "https://api.tavily.com/search";
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject body = new JSONObject();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("include_answer", true);
            body.put("max_results", 5);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int code = connection.getResponseCode();
            String raw = readResponseBody(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                String reason = extractErrorMessage(raw);
                return "搜索失败：HTTP " + code + (reason.isEmpty() ? "" : "，" + reason);
            }

            return formatSearchResult(query, raw);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "请求异常" : e.getMessage().trim();
            if (reason.isEmpty()) {
                reason = "请求异常";
            }
            return "搜索失败：" + reason;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String formatSearchResult(String query, String rawJson) {
        if (TextUtils.isEmpty(rawJson)) {
            return "搜索失败：返回内容为空";
        }

        try {
            JSONObject obj = new JSONObject(rawJson);
            String answer = safe(obj.optString("answer", "")).trim();
            JSONArray results = obj.optJSONArray("results");

            StringBuilder sb = new StringBuilder();
            sb.append("联网搜索结果（关键词=").append(query).append("）:\n");
            if (!answer.isEmpty()) {
                sb.append("结论：").append(answer).append("\n");
            }

            int appended = 0;
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject one = results.optJSONObject(i);
                    if (one == null) {
                        continue;
                    }
                    String title = safe(one.optString("title", "")).trim();
                    String url = safe(one.optString("url", "")).trim();
                    String content = safe(one.optString("content", "")).trim();
                    if (title.isEmpty() && url.isEmpty() && content.isEmpty()) {
                        continue;
                    }
                    appended++;
                    if (title.isEmpty()) {
                        title = "结果" + appended;
                    }
                    sb.append(appended).append(". ").append(title).append("\n");
                    if (!url.isEmpty()) {
                        sb.append("链接：").append(url).append("\n");
                    }
                    if (!content.isEmpty()) {
                        sb.append("摘要：").append(trimToLength(content, 140)).append("\n");
                    }
                }
            }

            if (appended == 0 && answer.isEmpty()) {
                return "未检索到相关结果";
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "搜索失败：解析返回数据失败";
        }
    }

    private static String extractErrorMessage(String rawJson) {
        if (TextUtils.isEmpty(rawJson)) {
            return "";
        }
        try {
            JSONObject obj = new JSONObject(rawJson);
            String detail = safe(obj.optString("detail", "")).trim();
            if (!detail.isEmpty()) {
                return detail;
            }
            String error = safe(obj.optString("error", "")).trim();
            if (!error.isEmpty()) {
                return error;
            }
            String message = safe(obj.optString("message", "")).trim();
            if (!message.isEmpty()) {
                return message;
            }
            return trimToLength(rawJson.replace('\n', ' ').trim(), 120);
        } catch (Exception ignored) {
            return trimToLength(rawJson.replace('\n', ' ').trim(), 120);
        }
    }

    private static String readResponseBody(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String trimToLength(String text, int maxLen) {
        String safe = safe(text);
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
