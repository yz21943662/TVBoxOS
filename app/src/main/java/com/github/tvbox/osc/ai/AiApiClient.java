package com.github.tvbox.osc.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiApiClient {
    private static final String TAG = "AiApiClient";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String API_KEY = "sk-19d69d034a8140789b1423bdab860123";
    private static final String MODEL = "qwen3.7-plus";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static AiApiClient instance;
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface AiCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    private AiApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized AiApiClient get() {
        if (instance == null) {
            instance = new AiApiClient();
        }
        return instance;
    }

    public void chat(String systemPrompt, String userMessage, AiCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", MODEL);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userMessage);
                messages.add(userMsg);

                body.add("messages", messages);
                body.addProperty("temperature", 0.7);
                body.addProperty("max_tokens", 2000);

                Request request = new Request.Builder()
                        .url(BASE_URL + "/chat/completions")
                        .header("Authorization", "Bearer " + API_KEY)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown error";
                    Log.e(TAG, "API error: " + response.code() + " " + errorBody);
                    notifyError(callback, "AI 服务请求失败: " + response.code());
                    return;
                }

                String respBody = response.body().string();
                JsonObject respJson = gson.fromJson(respBody, JsonObject.class);
                JsonArray choices = respJson.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject msg = choice.getAsJsonObject("message");
                    String content = msg.get("content").getAsString();
                    notifySuccess(callback, content);
                } else {
                    notifyError(callback, "AI 返回结果为空");
                }

            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                notifyError(callback, "网络请求失败: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                notifyError(callback, "请求异常: " + e.getMessage());
            }
        });
    }

    private void notifySuccess(AiCallback callback, String result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void notifyError(AiCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}