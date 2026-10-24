package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ai.AiApiClient;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiAnalysisActivity extends BaseActivity {
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

        tvTitle.setText("AI 观影分析");

        tvRefresh.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            startAnalysis();
        });

        startAnalysis();
    }

    private void startAnalysis() {
        showLoading();
        tvError.setVisibility(View.GONE);

        List<VodInfo> historyList = RoomDataManger.getAllVodRecord(100);
        if (historyList.isEmpty()) {
            showError("暂无观影记录，先去看看电影吧");
            return;
        }

        tvLoadingHint.setText("正在深度分析 " + historyList.size() + " 条观影记录...");

        Map<String, Integer> typeCount = new HashMap<>();
        StringBuilder viewingLog = new StringBuilder();
        for (int i = 0; i < historyList.size(); i++) {
            VodInfo v = historyList.get(i);
            viewingLog.append(i + 1).append(". 《").append(v.name).append("》");
            if (!TextUtils.isEmpty(v.type)) {
                viewingLog.append(" | 类型: ").append(v.type);
                typeCount.merge(v.type, 1, Integer::sum);
            }
            if (v.year > 0) {
                viewingLog.append(" | ").append(v.year).append("年");
            }
            if (!TextUtils.isEmpty(v.lang)) {
                viewingLog.append(" | ").append(v.lang);
            }
            if (!TextUtils.isEmpty(v.playNote)) {
                viewingLog.append(" | 看到: ").append(v.playNote);
            }
            if (!TextUtils.isEmpty(v.actor)) {
                viewingLog.append(" | 演员: ").append(v.actor);
            }
            if (!TextUtils.isEmpty(v.director)) {
                viewingLog.append(" | 导演: ").append(v.director);
            }
            viewingLog.append("\n");
        }

        String favoriteType = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                favoriteType = e.getKey();
            }
        }

        String systemPrompt = "你是一个专业的影视推荐分析师。用户会给你他的观影记录，请你分析用户的观影偏好并给出个性化推荐。\n" +
                "分析维度：\n" +
                "1. 观影偏好总结（喜欢的类型、风格、导演/演员偏好等）\n" +
                "2. 观影习惯分析（观看频率、类型偏好等）\n" +
                "3. 基于偏好的 3-5 部相似推荐（推荐热门的同类型影片）\n" +
                "4. 拓展推荐（建议尝试的新类型，附推荐影片）\n" +
                "请用中文回答，格式清晰，每条推荐包含片名和推荐理由。";

        StringBuilder userMsg = new StringBuilder();
        if (!TextUtils.isEmpty(favoriteType)) {
            userMsg.append("用户最常看的类型是: ").append(favoriteType).append(" (共").append(maxCount).append("部)\n\n");
        }
        userMsg.append("以下是用户的完整观影记录：\n\n");
        userMsg.append(viewingLog);
        userMsg.append("\n请基于以上记录进行分析。");

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

    private void showLoading() {
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