package cn.edu.hut.course;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.model.Model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class AiGateway {

    private AiGateway() {
    }

    public static String verifyApiKeyByProvider(String provider, String baseUrl, String apiKey) throws Exception {
        if (AiConfigStore.PROVIDER_SDK.equals(provider)) {
            return verifyApiKeyWithSdk(apiKey);
        }
        return verifyApiKeyWithCurl(baseUrl, apiKey);
    }

    public static String chat(String provider, String baseUrl, String apiKey, String model,
                              String systemPrompt, String userPrompt) throws Exception {
        if (AiConfigStore.PROVIDER_SDK.equals(provider)) {
            try {
                return chatWithSdk(apiKey, model, systemPrompt, userPrompt);
            } catch (Exception sdkEx) {
                // SDK 失败时回退到 OpenAI 兼容接口，保证可用性。
                return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt);
            }
        }
        return chatWithCurl(baseUrl, apiKey, model, systemPrompt, userPrompt);
    }

    private static String verifyApiKeyWithSdk(String apiKey) throws Exception {
        OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(20));
        try {
            List<Model> models = service.listModels();
            if (models == null || models.isEmpty()) {
                throw new IllegalStateException("SDK校验失败：models.list 为空");
            }
            String modelId = models.get(0).getId();
            return "SDK校验成功，models.list() 可用，示例模型：" + modelId;
        } finally {
            service.shutdownExecutor();
        }
    }

    private static String chatWithSdk(String apiKey, String model, String systemPrompt, String userPrompt) throws Exception {
        OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(45));
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", systemPrompt));
            messages.add(new ChatMessage("user", userPrompt));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.2)
                    .build();

            ChatCompletionResult result = service.createChatCompletion(request);
            if (result.getChoices() == null || result.getChoices().isEmpty() || result.getChoices().get(0).getMessage() == null) {
                throw new IllegalStateException("SDK对话返回为空");
            }
            String content = result.getChoices().get(0).getMessage().getContent();
            return content == null || content.trim().isEmpty() ? "模型返回为空" : content.trim();
        } finally {
            service.shutdownExecutor();
        }
    }

    private static String verifyApiKeyWithCurl(String baseUrl, String apiKey) throws Exception {
        String endpoint = buildEndpoint(baseUrl, "/models");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            int code = conn.getResponseCode();
            String body = readBody(conn, code >= 200 && code < 300);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " - " + body);
            }
            JSONObject obj = new JSONObject(body);
            JSONArray data = obj.optJSONArray("data");
            if (data == null || data.length() == 0) {
                throw new IllegalStateException("Curl校验失败：/models 返回空");
            }
            return "Curl校验成功，/models 可用，模型数量：" + data.length();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String chatWithCurl(String baseUrl, String apiKey, String model,
                                       String systemPrompt, String userPrompt) throws Exception {
        String endpoint = buildEndpoint(baseUrl, "/chat/completions");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("model", model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            payload.put("messages", messages);
            payload.put("temperature", 0.2);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = readBody(conn, code >= 200 && code < 300);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " - " + body);
            }

            JSONObject obj = new JSONObject(body);
            JSONArray choices = obj.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return "模型返回为空";
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject msg = first.optJSONObject("message");
            if (msg == null) {
                return first.optString("text", "模型返回为空");
            }
            return msg.optString("content", "模型返回为空").trim();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String buildEndpoint(String baseUrl, String path) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (url.isEmpty()) {
            url = "https://api.openai.com/v1";
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url + path;
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
}
