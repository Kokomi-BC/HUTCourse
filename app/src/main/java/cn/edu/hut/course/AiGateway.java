package cn.edu.hut.course;

import android.util.Log;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AiGateway {

    private static final String TAG = "AiGateway";
    private static final String EMPTY_MODEL_RESPONSE = "模型返回为空";
    private static final int LOG_SNIPPET_LIMIT = 1200;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int IMAGE_CONNECT_TIMEOUT_MS = 30_000;
    private static final int IMAGE_READ_TIMEOUT_MS = 180_000;

    public static final class RequestCacheHint {
        public final String conversationId;
        public final String promptCacheKey;

        public RequestCacheHint(String conversationId, String promptCacheKey) {
            this.conversationId = safe(conversationId).trim();
            this.promptCacheKey = safe(promptCacheKey).trim();
        }
    }

    private AiGateway() {
    }

    public static String chat(String provider, String baseUrl, String apiKey, String model,
                              String systemPrompt, String userPrompt) throws Exception {
        return chat(provider, baseUrl, apiKey, model, systemPrompt, userPrompt, (List<String>) null, null);
    }

    public static String chat(String provider, String baseUrl, String apiKey, String model,
                              String systemPrompt, String userPrompt, String latestImagePath) throws Exception {
        List<String> images = new ArrayList<>();
        if (latestImagePath != null && !latestImagePath.trim().isEmpty()) {
            images.add(latestImagePath);
        }
        return chat(provider, baseUrl, apiKey, model, systemPrompt, userPrompt, images, null);
    }

    public static String chat(String provider, String baseUrl, String apiKey, String model,
                              String systemPrompt, String userPrompt, List<String> imagePaths) throws Exception {
        return chat(provider, baseUrl, apiKey, model, systemPrompt, userPrompt, imagePaths, null);
    }

    public static String chat(String provider, String baseUrl, String apiKey, String model,
                              String systemPrompt, String userPrompt, List<String> imagePaths,
                              RequestCacheHint cacheHint) throws Exception {
        boolean hasImage = imagePaths != null && !imagePaths.isEmpty();
        if (AiConfigStore.PROVIDER_SDK.equals(provider)) {
            if (hasImage) {
                // 现有 SDK 版本对多模态消息支持有限，图文请求统一走兼容接口。
                return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt, imagePaths, cacheHint);
            }
            try {
                return chatWithSdk(apiKey, model, systemPrompt, userPrompt, cacheHint);
            } catch (Exception sdkEx) {
                // SDK 失败时回退到 OpenAI 兼容接口，保证可用性。
                return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt, null, cacheHint);
            }
        }
        if (hasImage) {
            return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt, imagePaths, cacheHint);
        }
        return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt, null, cacheHint);
    }

    private static String chatWithSdk(String apiKey, String model, String systemPrompt,
                                      String userPrompt, RequestCacheHint cacheHint) throws Exception {
        OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(45));
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", systemPrompt));
            messages.add(new ChatMessage("user", userPrompt));

            ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.2);

            if (cacheHint != null && !cacheHint.conversationId.isEmpty()) {
                try {
                    builder.getClass().getMethod("user", String.class).invoke(builder, cacheHint.conversationId);
                } catch (Throwable ignored) {
                    // 低版本 SDK 可能无 user 字段，忽略并继续。
                }
            }

            ChatCompletionRequest request = builder.build();

            ChatCompletionResult result = service.createChatCompletion(request);
            if (result.getChoices() == null || result.getChoices().isEmpty() || result.getChoices().get(0).getMessage() == null) {
                throw new IllegalStateException("SDK对话返回为空");
            }
            String content = result.getChoices().get(0).getMessage().getContent();
            return content == null || content.trim().isEmpty() ? EMPTY_MODEL_RESPONSE : content.trim();
        } finally {
            service.shutdownExecutor();
        }
    }

    private static String chatWithCurl(String baseUrl, String apiKey, String model,
                                       String systemPrompt, String userPrompt,
                                       List<String> imagePaths,
                                       RequestCacheHint cacheHint) throws Exception {
        String endpoint = buildEndpoint(baseUrl, "/chat/completions");
        boolean hasImage = imagePaths != null && !imagePaths.isEmpty();
        String conversationId = cacheHint == null ? "" : safe(cacheHint.conversationId);
        String promptCacheKey = cacheHint == null ? "" : safe(cacheHint.promptCacheKey);
        int imageCount = Math.min(4, imagePaths == null ? 0 : imagePaths.size());
        long totalImageBytes = sumSourceImageBytes(imagePaths);
        HttpURLConnection conn = null;
        try {
            Log.i(TAG, "chatWithCurl start endpoint=" + endpoint
                    + ", model=" + safe(model)
                    + ", hasImage=" + hasImage
                + ", conversationIdPresent=" + !conversationId.isEmpty()
                + ", cacheKeyPresent=" + !promptCacheKey.isEmpty()
                    + ", imageCount=" + imageCount
                    + ", imageBytes=" + totalImageBytes
                    + ", promptLen=" + (userPrompt == null ? 0 : userPrompt.length()));

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(hasImage ? IMAGE_CONNECT_TIMEOUT_MS : CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(hasImage ? IMAGE_READ_TIMEOUT_MS : READ_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (!conversationId.isEmpty()) {
                conn.setRequestProperty("X-Conversation-Id", conversationId);
                conn.setRequestProperty("X-Session-Id", conversationId);
            }
            if (!promptCacheKey.isEmpty()) {
                conn.setRequestProperty("X-Prompt-Cache-Key", promptCacheKey);
            }

            JSONObject payload = new JSONObject();
            payload.put("model", model);
            if (!conversationId.isEmpty()) {
                payload.put("user", conversationId);
            }
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            if (hasImage) {
                JSONArray userContent = new JSONArray();
                userContent.put(new JSONObject().put("type", "text").put("text", userPrompt));
                int limit = Math.min(4, imagePaths.size());
                for (int i = 0; i < limit; i++) {
                    String one = imagePaths.get(i);
                    if (one == null || one.trim().isEmpty()) {
                        continue;
                    }
                    userContent.put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", toDataUrl(one))));
                }
                messages.put(new JSONObject().put("role", "user").put("content", userContent));
            } else {
                messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            }
            payload.put("messages", messages);
            payload.put("temperature", 0.2);
            payload.put("stream", false);

            byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payloadBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadBytes);
            }

            int code = conn.getResponseCode();
            String body = readBody(conn, code >= 200 && code < 300);
            Log.i(TAG, "chatWithCurl response code=" + code + ", bodyLen=" + (body == null ? 0 : body.length())
                    + ", hasImage=" + hasImage);
            if (code < 200 || code >= 300) {
                Log.w(TAG, "chatWithCurl non-2xx body=" + clipForLog(body));
                throw new IllegalStateException("HTTP " + code + " - " + body);
            }

            JSONObject obj;
            try {
                obj = new JSONObject(body);
            } catch (Exception jsonEx) {
                Log.w(TAG, "chatWithCurl invalid json, body=" + clipForLog(body), jsonEx);
                throw jsonEx;
            }

            String apiError = extractApiErrorMessage(obj);
            if (!apiError.isEmpty()) {
                Log.w(TAG, "chatWithCurl api error=" + clipForLog(apiError));
                return "模型服务返回错误：" + apiError;
            }

            String topLevelFallback = extractTopLevelResponseText(obj);
            JSONArray choices = obj.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                if (topLevelFallback.isEmpty()) {
                    Log.w(TAG, "chatWithCurl empty choices and no fallback, body=" + clipForLog(body));
                }
                return topLevelFallback.isEmpty() ? EMPTY_MODEL_RESPONSE : topLevelFallback;
            }

            JSONObject first = choices.optJSONObject(0);
            if (first == null) {
                Set<String> firstFragments = new LinkedHashSet<>();
                collectTextFragments(choices.opt(0), firstFragments, 0);
                String fallback = joinFragments(firstFragments);
                if (fallback.isEmpty()) {
                    fallback = topLevelFallback;
                }
                if (fallback.isEmpty()) {
                    Log.w(TAG, "chatWithCurl first choice is not object and no fallback, body=" + clipForLog(body));
                }
                return fallback.isEmpty() ? EMPTY_MODEL_RESPONSE : fallback;
            }

            JSONObject msg = first.optJSONObject("message");
            if (msg == null) {
                String fallback = extractTopLevelResponseText(first);
                if (fallback.isEmpty()) {
                    fallback = first.optString("text", "").trim();
                }
                if (fallback.isEmpty()) {
                    fallback = topLevelFallback;
                }
                if (fallback.isEmpty()) {
                    Log.w(TAG, "chatWithCurl missing message object and no fallback, body=" + clipForLog(body));
                }
                return fallback.isEmpty() ? EMPTY_MODEL_RESPONSE : fallback;
            }

            String content = extractMessageContent(msg);
            if (content.isEmpty()) {
                content = extractTopLevelResponseText(first);
            }
            if (content.isEmpty()) {
                content = first.optString("text", "").trim();
            }
            if (content.isEmpty()) {
                content = topLevelFallback;
            }

            if (content.isEmpty()) {
                String finishReason = safe(first.optString("finish_reason", "")).trim();
                if (!finishReason.isEmpty()) {
                    Log.w(TAG, "chatWithCurl empty content, finishReason=" + finishReason + ", body=" + clipForLog(body));
                    String mappedReason = mapFinishReasonToMessage(finishReason, hasImage);
                    if (!mappedReason.isEmpty()) {
                        return mappedReason;
                    }
                } else {
                    Log.w(TAG, "chatWithCurl empty content without finishReason, body=" + clipForLog(body));
                }
                return EMPTY_MODEL_RESPONSE;
            }
            return content;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractMessageContent(JSONObject msg) {
        if (msg == null) {
            return "";
        }
        Object rawContent = msg.opt("content");
        if (rawContent instanceof String) {
            String plain = ((String) rawContent).trim();
            if (!plain.isEmpty() && !"null".equalsIgnoreCase(plain)) {
                return plain;
            }
        }

        Set<String> fragments = new LinkedHashSet<>();
        collectTextFragments(msg.opt("content"), fragments, 0);
        collectTextFragments(msg.opt("text"), fragments, 0);
        collectTextFragments(msg.opt("output_text"), fragments, 0);
        collectTextFragments(msg.opt("reasoning_content"), fragments, 0);
        collectTextFragments(msg.opt("refusal"), fragments, 0);
        collectTextFragments(msg.opt("result"), fragments, 0);
        collectTextFragments(msg.opt("output"), fragments, 0);
        String joined = joinFragments(fragments);
        if (!joined.isEmpty()) {
            return joined;
        }

        String refusal = msg.optString("refusal", "").trim();
        if ("null".equalsIgnoreCase(refusal)) {
            return "";
        }
        return refusal;
    }

    private static String extractTopLevelResponseText(JSONObject obj) {
        if (obj == null) {
            return "";
        }
        Set<String> fragments = new LinkedHashSet<>();
        collectTextFragments(obj.opt("output_text"), fragments, 0);
        collectTextFragments(obj.opt("text"), fragments, 0);
        collectTextFragments(obj.opt("response"), fragments, 0);
        collectTextFragments(obj.opt("answer"), fragments, 0);
        collectTextFragments(obj.opt("message"), fragments, 0);
        collectTextFragments(obj.opt("output"), fragments, 0);
        collectTextFragments(obj.opt("delta"), fragments, 0);
        collectTextFragments(obj.opt("result"), fragments, 0);
        return joinFragments(fragments);
    }

    private static String extractApiErrorMessage(JSONObject obj) {
        if (obj == null) {
            return "";
        }
        Object error = obj.opt("error");
        if (error == null || error == JSONObject.NULL) {
            return "";
        }
        Set<String> fragments = new LinkedHashSet<>();
        collectTextFragments(error, fragments, 0);
        String joined = joinFragments(fragments);
        if (!joined.isEmpty()) {
            return joined;
        }
        if (error instanceof JSONObject) {
            JSONObject errObj = (JSONObject) error;
            String direct = safe(errObj.optString("message", "")).trim();
            if (!direct.isEmpty()) {
                return direct;
            }
        }
        return safe(String.valueOf(error)).trim();
    }

    private static String joinFragments(Set<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String one : fragments) {
            String piece = one == null ? "" : one.trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(piece);
        }
        return sb.toString().trim();
    }

    private static void collectTextFragments(Object value, Set<String> out, int depth) {
        if (out == null || value == null || value == JSONObject.NULL || depth > 8) {
            return;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                out.add(text);
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) {
                collectTextFragments(arr.opt(i), out, depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) {
            return;
        }

        JSONObject obj = (JSONObject) value;
        String type = obj.optString("type", "").trim().toLowerCase(Locale.ROOT);
        if (type.contains("image")) {
            return;
        }

        collectTextFragments(obj.opt("text"), out, depth + 1);
        collectTextFragments(obj.opt("value"), out, depth + 1);
        collectTextFragments(obj.opt("content"), out, depth + 1);
        collectTextFragments(obj.opt("output_text"), out, depth + 1);
        collectTextFragments(obj.opt("reasoning_content"), out, depth + 1);
        collectTextFragments(obj.opt("message"), out, depth + 1);
        collectTextFragments(obj.opt("delta"), out, depth + 1);
        collectTextFragments(obj.opt("response"), out, depth + 1);
        collectTextFragments(obj.opt("answer"), out, depth + 1);

        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (shouldSkipGenericTextKey(key)) {
                continue;
            }
            collectTextFragments(obj.opt(key), out, depth + 1);
        }
    }

    private static boolean shouldSkipGenericTextKey(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.trim().toLowerCase(Locale.ROOT);
        return lower.isEmpty()
                || "id".equals(lower)
                || "object".equals(lower)
                || "model".equals(lower)
                || "created".equals(lower)
                || "index".equals(lower)
                || "role".equals(lower)
                || "type".equals(lower)
                || "status".equals(lower)
                || "usage".equals(lower)
                || lower.endsWith("_tokens")
                || "finish_reason".equals(lower)
                || "tool_calls".equals(lower)
                || "function_call".equals(lower)
                || "arguments".equals(lower)
                || "image_url".equals(lower)
                || "url".equals(lower)
                || "mime_type".equals(lower)
                || "mime".equals(lower);
    }

    private static long sumSourceImageBytes(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        int limit = Math.min(4, imagePaths.size());
        for (int i = 0; i < limit; i++) {
            String one = imagePaths.get(i);
            if (one == null || one.trim().isEmpty()) {
                continue;
            }
            File file = new File(one);
            if (file.exists() && file.isFile()) {
                total += Math.max(0L, file.length());
            }
        }
        return total;
    }

    private static String mapFinishReasonToMessage(String finishReason, boolean hasImage) {
        String reason = safe(finishReason).trim().toLowerCase(Locale.ROOT);
        if (reason.isEmpty()) {
            return "";
        }
        if ("content_filter".equals(reason)) {
            return hasImage
                    ? "模型回复被内容安全策略拦截（content_filter），请更换图片或降低敏感内容后重试。"
                    : "模型回复被内容安全策略拦截（content_filter），请调整提问内容后重试。";
        }
        if ("length".equals(reason)) {
            return "模型输出长度达到上限（length），请缩短问题或分步提问。";
        }
        if ("safety".equals(reason)) {
            return "模型回复触发安全策略（safety），请调整请求内容后重试。";
        }
        return "";
    }

    private static String clipForLog(String text) {
        String safeText = safe(text).replace('\n', ' ').replace('\r', ' ');
        if (safeText.length() <= LOG_SNIPPET_LIMIT) {
            return safeText;
        }
        return safeText.substring(0, LOG_SNIPPET_LIMIT) + "...(truncated)";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String buildEndpoint(String baseUrl, String path) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (url.isEmpty()) {
            url = "https://api.openai.com/v1";
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String normalizedPath = path == null ? "" : path.trim();
        if (!normalizedPath.isEmpty() && !normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return url + normalizedPath;
    }

    private static String readBody(HttpURLConnection conn, boolean success) throws Exception {
        InputStream stream = success ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String toDataUrl(String imagePath) throws Exception {
        File file = new File(imagePath == null ? "" : imagePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalStateException("图片文件不存在：" + imagePath);
        }
        byte[] data = readFileBytes(file);
        if (data.length == 0) {
            throw new IllegalStateException("图片内容为空");
        }
        String base64 = Base64.getEncoder().encodeToString(data);
        return "data:" + guessMimeType(file.getName()) + ";base64," + base64;
    }

    private static byte[] readFileBytes(File file) throws Exception {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String guessMimeType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
