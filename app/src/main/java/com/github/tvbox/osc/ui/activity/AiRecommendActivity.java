package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ai.AiApiClient;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AiRecommendActivity extends BaseActivity {
    private TextView tvTitle, tvResult, tvError, tvRefresh, tvLoadingHint;
    private ScrollView scrollResult;
    private View llLoading;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_ai_result;
    }

    @Override
    protected void init() {
        tvTitle = findViewById(R.id.tvTitle);
        tvResult = findViewById(R.id.tvResult);
        tvError = findViewById(R.id.tvError);
        tvRefresh = findViewById(R.id.tvRefresh);
        tvLoadingHint = findViewById(R.id.tvLoadingHint);
        scrollResult = findViewById(R.id.scrollResult);
        llLoading = findViewById(R.id.llLoading);

        tvTitle.setText("AI 荐片");

        tvRefresh.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            startAnalysis();
        });

        // 自动开始分析
        startAnalysis();
    }

    private void startAnalysis() {
        showLoading();
        tvError.setVisibility(View.GONE);

        // 从豆瓣热播获取数据作为首页影片列表
        String doubanJson = Hawk.get("home_hot", "");
        List<String> movieList = new ArrayList<>();
        if (!TextUtils.isEmpty(doubanJson)) {
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.JsonObject infoJson = gson.fromJson(doubanJson, com.google.gson.JsonObject.class);
                com.google.gson.JsonArray array = infoJson.getAsJsonArray("data");
                int limit = Math.min(array.size(), 20);
                for (int i = 0; i < limit; i++) {
                    com.google.gson.JsonObject obj = array.get(i).getAsJsonObject();
                    String name = obj.get("title").getAsString();
                    String rate = obj.has("rate") ? obj.get("rate").getAsString() : "暂无评分";
                    movieList.add(name + " (评分: " + rate + ")");
                }
            } catch (Exception ignored) {}
        }

        if (movieList.isEmpty()) {
            showError("暂无首页影片数据，请先回到首页加载数据");
            return;
        }

        tvLoadingHint.setText("正在分析 " + movieList.size() + " 部影片...");

        Calendar cal = Calendar.getInstance();
        String today = String.format("%d年%d月%d日", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE));

        String systemPrompt = "你是一个专业的影视推荐助手。用户会给你当前热播的电影列表（包含片名和评分），请你：\n" +
                "1. 从列表中筛选出评分最高的 3 部影片\n" +
                "2. 推荐 3 部最新最热门的影片\n" +
                "3. 给出一条综合观影建议\n" +
                "请用中文回答，格式清晰，每条推荐包含：片名、评分、推荐理由。每部影片的推荐理由控制在 30 字以内。";

        StringBuilder userMsg = new StringBuilder();
        userMsg.append("今天是 ").append(today).append("，以下是当前热播影片列表：\n\n");
        for (int i = 0; i < movieList.size(); i++) {
            userMsg.append(i + 1).append(". ").append(movieList.get(i)).append("\n");
        }
        userMsg.append("\n请帮我分析并推荐值得观看的影片。");

        AiApiClient.get().chat(systemPrompt, userMsg.toString(), new AiApiClient.AiCallback() {
            @Override
            public void onSuccess(String result) {
                showResult(result);
            }

            @Override
            public void onError(String error) {
                showError(error);
            }
        });
    }

    protected void showLoading() {
        llLoading.setVisibility(View.VISIBLE);
        scrollResult.setVisibility(View.GONE);
    }

    private void showResult(String text) {
        llLoading.setVisibility(View.GONE);
        scrollResult.setVisibility(View.VISIBLE);
        tvResult.setText(text);
    }

    private void showError(String msg) {
        llLoading.setVisibility(View.GONE);
        scrollResult.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }
}