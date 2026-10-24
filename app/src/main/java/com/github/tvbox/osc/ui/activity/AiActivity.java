package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.FastClickCheckUtil;

public class AiActivity extends BaseActivity {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_ai;
    }

    @Override
    protected void init() {
        findViewById(R.id.llRecommend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(AiRecommendActivity.class);
            }
        });
        findViewById(R.id.llTracking).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(AiTrackingActivity.class);
            }
        });
        findViewById(R.id.llAnalysis).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(AiAnalysisActivity.class);
            }
        });
    }
}