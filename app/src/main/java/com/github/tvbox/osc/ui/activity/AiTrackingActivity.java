package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

public class AiTrackingActivity extends BaseActivity {
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

        tvTitle.setText("AI 追剧");

        tvRefresh.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            startAnalysis();
        });

        startAnalysis();
    }

    private void startAnalysis() {
        showLoading();
        tvError.setVisibility(View.GONE);

        List<VodInfo> historyList = RoomDataManger.getAllVodRecord(50);
        if (historyList.isEmpty()) {
            showError("暂无观看记录，先去看看电影吧");
            return;
        }

        tvLoadingHint.setText("正在分析 " + historyList.size() + " 条观看记录...");

        StringBuilder viewingInfo = new StringBuilder();
        for (int i = 0; i < historyList.size(); i++) {
            VodInfo v = historyList.get(i);
            viewingInfo.append(i + 1).append(". 《").append(v.name).append("》");
            if (!TextUtils.isEmpty(v.type)) {
                viewingInfo.append(" | 类型: ").append(v.type);
            }
            if (v.year > 0) {
                viewingInfo.append(" | ").append(v.year).append("年");
            }
            if (!TextUtils.isEmpty(v.playNote)) {
                viewingInfo.append(" | 看到: ").append(v.playNote);
            }
            if (!TextUtils.isEmpty(v.note)) {
                viewingInfo.append(" | 总集数: ").append(v.note);
            }
            if (!TextUtils.isEmpty(v.last)) {
                viewingInfo.append(" | 更新时间: ").append(v.last);
            }
            viewingInfo.append("\n");
        }

        String systemPrompt = "你是一个贴心的追剧管理助手。用户会给你他的电视剧/电影观看记录列表，请你：\n" +
                "1. 整理出当前正在追的剧集清单（看过的、可能还在更新中的）\n" +
                "2. 标注每部的观看进度（看到第几集了）\n" +
                "3. 给出追剧建议，比如哪些剧可能已经更新了新集数、哪些值得继续追\n" +
                "4. 最后给出一条总结性追剧建议\n" +
                "请用中文回答，格式清晰有条理。";

        AiApiClient.get().chat(systemPrompt, viewingInfo.toString(), new AiApiClient.AiCallback() {
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