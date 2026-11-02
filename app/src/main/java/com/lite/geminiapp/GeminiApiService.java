package com.lite.geminiapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiApiService {

    public static final String DEFAULT_API_KEY = BuildConfig.DEFAULT_API_KEY;
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    // Sequential Fallback Models ordered by priority (Latest powerful & verified models first)
    public static final String[] FALLBACK_MODELS = {
            "gemini-2.5-flash",          // 1. Primary flagship fast model (Active & verified)
            "gemini-2.5-flash-lite",     // 2. Ultra-fast, low-latency lite model (Active & verified)
            "gemini-3.5-flash-lite",     // 3. Next-gen lite model (Active & verified)
            "gemini-flash-lite-latest",  // 4. Latest flash-lite alias (Active & verified)
            "gemini-3-flash-preview",    // 5. Gemini 3 flash preview (Active & verified)
            "gemini-3.5-flash",          // 6. Gemini 3.5 flash (Active)
            "gemini-flash-latest",       // 7. General flash latest
            "gemini-2.5-pro"             // 8. Pro model fallback
    };

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface GeminiFallbackCallback {
        void onTryingModel(String modelName, int index, int total);
        void onFallback(String failedModel, String errorReason, String nextModel);
        void onSuccess(String responseText, String successfulModel);
        void onError(String errorMessage);
    }

    private interface SingleModelCallback {
        void onSuccess(String text);
        void onError(String errorMsg);
    }

    public GeminiApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void generateResponseWithFallback(
            String apiKey,
            String promptText,
            String base64Image,
            String mimeType,
            GeminiFallbackCallback callback) {

        final String effectiveKey = (apiKey != null && !apiKey.trim().isEmpty())
                ? apiKey.trim()
                : DEFAULT_API_KEY;

        attemptModel(effectiveKey, promptText, base64Image, mimeType, 0, new StringBuilder(), callback);
    }

    private void attemptModel(
            String apiKey,
            String promptText,
            String base64Image,
            String mimeType,
            int modelIndex,
            StringBuilder failureLog,
            GeminiFallbackCallback callback) {

        if (modelIndex >= FALLBACK_MODELS.length) {
            mainHandler.post(() -> callback.onError("All fallback models failed:\n" + failureLog.toString()));
            return;
        }

        String currentModel = FALLBACK_MODELS[modelIndex];
        mainHandler.post(() -> callback.onTryingModel(currentModel, modelIndex + 1, FALLBACK_MODELS.length));

        executeSingleRequest(apiKey, currentModel, promptText, base64Image, mimeType, new SingleModelCallback() {
            @Override
            public void onSuccess(String text) {
                mainHandler.post(() -> callback.onSuccess(text, currentModel));
            }

            @Override
            public void onError(String errorMsg) {
                failureLog.append("• ").append(currentModel).append(": ").append(errorMsg).append("\n");
                if (modelIndex + 1 < FALLBACK_MODELS.length) {
                    String nextModel = FALLBACK_MODELS[modelIndex + 1];
                    mainHandler.post(() -> callback.onFallback(currentModel, errorMsg, nextModel));
                }
                // Sequential fallback to next model
                attemptModel(apiKey, promptText, base64Image, mimeType, modelIndex + 1, failureLog, callback);
            }
        });
    }

    private void executeSingleRequest(
            String apiKey,
            String modelName,
            String promptText,
            String base64Image,
            String mimeType,
            SingleModelCallback callback) {

        try {
            JSONObject root = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();

            // Text Prompt
            if (promptText != null && !promptText.trim().isEmpty()) {
                JSONObject textPart = new JSONObject();
                textPart.put("text", promptText);
                parts.put(textPart);
            }

            // Multimodal Image (if provided)
            if (base64Image != null && !base64Image.isEmpty()) {
                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", (mimeType != null ? mimeType : "image/jpeg"));
                inlineData.put("data", base64Image);
                imagePart.put("inline_data", inlineData);
                parts.put(imagePart);
            }

            contentObj.put("parts", parts);
            contents.put(contentObj);
            root.put("contents", contents);

            String requestUrl = BASE_URL + modelName + ":generateContent?key=" + apiKey.trim();

            RequestBody body = RequestBody.create(root.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Connection failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (response) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            String err = parseErrorMessage(responseBody, response.code());
                            callback.onError(err);
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);
                        JSONArray candidates = json.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);
                            JSONObject content = candidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray resParts = content.optJSONArray("parts");
                                if (resParts != null && resParts.length() > 0) {
                                    String text = resParts.getJSONObject(0).optString("text", "");
                                    callback.onSuccess(text);
                                    return;
                                }
                            }
                        }
                        callback.onError("Empty response");
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Build error: " + e.getMessage());
        }
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                return error.optString("message", "HTTP " + statusCode);
            }
        } catch (Exception ignored) {}
        return "HTTP error " + statusCode;
    }
}
