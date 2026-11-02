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

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface GeminiCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    public GeminiApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void generateResponse(String apiKey, String promptText, String base64Image, String mimeType, GeminiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("API Key is missing. Please enter your Gemini API Key in Settings.");
            return;
        }

        try {
            JSONObject root = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();

            // 1. Text part
            if (promptText != null && !promptText.trim().isEmpty()) {
                JSONObject textPart = new JSONObject();
                textPart.put("text", promptText);
                parts.put(textPart);
            }

            // 2. Multimodal Image part (if provided)
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

            String requestUrl = BASE_URL + MODEL_NAME + ":generateContent?key=" + apiKey.trim();

            RequestBody body = RequestBody.create(root.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (response) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            String err = parseErrorMessage(responseBody, response.code());
                            mainHandler.post(() -> callback.onError(err));
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);
                        JSONArray candidates = json.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);
                            JSONObject content = candidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String text = parts.getJSONObject(0).optString("text", "");
                                    mainHandler.post(() -> callback.onSuccess(text));
                                    return;
                                }
                            }
                        }
                        mainHandler.post(() -> callback.onError("No response text received from Gemini."));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Parsing error: " + e.getMessage()));
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Request creation error: " + e.getMessage());
        }
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                return error.optString("message", "Error " + statusCode);
            }
        } catch (Exception ignored) {}
        return "Server returned status code: " + statusCode;
    }
}
