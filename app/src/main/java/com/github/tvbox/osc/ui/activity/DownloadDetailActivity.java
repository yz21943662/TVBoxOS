package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.download.DownloadManager;
import com.github.tvbox.osc.download.DownloadTask;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Map;

public class DownloadDetailActivity extends BaseActivity {
    private TextView tvName;
    private ImageView tvThumb;
    private TextView tvPlay;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewFlag;
    private SeriesAdapter seriesAdapter;
    private SeriesFlagAdapter seriesFlagAdapter;
    private SourceViewModel sourceViewModel;
    private VodInfo vodInfo;
    private String sourceKey;
    private String vid;
    private String title;
    private String picture;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_detail;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            vid = bundle.getString("id", "");
            sourceKey = bundle.getString("sourceKey", "");
            title = bundle.getString("title", "");
            picture = bundle.getString("picture", "");
        }

        tvName = findViewById(R.id.tvName);
        tvThumb = findViewById(R.id.tvThumb);
        tvPlay = findViewById(R.id.tvPlay);
        mGridView = findViewById(R.id.mGridView);
        mGridViewFlag = findViewById(R.id.mGridViewFlag);

        // 隐藏顶部预览播放界面和不相关按钮
        View previewPlayer = findViewById(R.id.previewPlayer);
        if (previewPlayer != null) previewPlayer.setVisibility(View.GONE);
        View topLayout = findViewById(R.id.topLayout);
        if (topLayout != null) topLayout.setVisibility(View.VISIBLE);

        // 隐藏操作按钮行中不相关的
        hideViewById(R.id.tvQuickSearch);
        hideViewById(R.id.tvDesc);
        hideViewById(R.id.tvChangeSource);

        // 修改"立即播放"为"下载所选剧集"
        tvPlay.setText("下载所选剧集");
        tvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                downloadCurrentEpisode();
            }
        });

        // 剧集旗标
        mGridViewFlag.setHasFixedSize(true);
        mGridViewFlag.setLayoutManager(new com.owen.tvrecyclerview.widget.V7LinearLayoutManager(this, com.owen.tvrecyclerview.widget.V7LinearLayoutManager.HORIZONTAL, false));
        seriesFlagAdapter = new SeriesFlagAdapter();
        mGridViewFlag.setAdapter(seriesFlagAdapter);
        seriesFlagAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (vodInfo != null && vodInfo.seriesFlags != null && position < vodInfo.seriesFlags.size()) {
                    String newFlag = vodInfo.seriesFlags.get(position);
                    if (vodInfo.seriesMap.containsKey(newFlag)) {
                        vodInfo.playFlag = newFlag;
                        refreshSeriesList();
                    }
                }
            }
        });

        // 剧集列表
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this, 6));
        seriesAdapter = new SeriesAdapter();
        mGridView.setAdapter(seriesAdapter);
        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (vodInfo != null && vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
                    java.util.List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
                    if (position < series.size()) {
                        for (int i = 0; i < series.size(); i++) {
                            seriesAdapter.getData().get(i).selected = false;
                        }
                        series.get(position).selected = true;
                        seriesAdapter.notifyDataSetChanged();
                        vodInfo.playIndex = position;
                    }
                }
            }
        });

        // 隐藏预览相关
        hideViewById(R.id.showPreview);
        hideViewById(R.id.mSeriesSortTv);

        // 加载详情
        tvName.setText(title);
        if (!TextUtils.isEmpty(picture)) {
            ImgUtil.load(picture, tvThumb, 10);
        }
        loadDetail();
    }

    private void hideViewById(int id) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    private void loadDetail() {
        showLoading();
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.video != null && absXml.video.sourceKey != null && absXml.video.sourceKey.equals(sourceKey)) {
                    vodInfo = absXml.video;
                    if (vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
                        // 没有剧集列表，直接下载当前影片
                        Toast.makeText(DownloadDetailActivity.this, "没有剧集列表，无法选择下载", Toast.LENGTH_SHORT).show();
                        showEmpty();
                        return;
                    }
                    showSuccess();
                    refreshSeriesFlags();
                    refreshSeriesList();
                }
            }
        });
        sourceViewModel.getDetail(sourceKey, vid);
    }

    private void refreshSeriesFlags() {
        if (vodInfo.seriesFlags != null && !vodInfo.seriesFlags.isEmpty()) {
            seriesFlagAdapter.setNewData(new ArrayList<>(vodInfo.seriesFlags));
            if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
                vodInfo.playFlag = vodInfo.seriesFlags.get(0);
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshSeriesList() {
        if (vodInfo != null && vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
            java.util.List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (vodInfo.playIndex >= 0 && vodInfo.playIndex < series.size()) {
                series.get(vodInfo.playIndex).selected = true;
            }
            seriesAdapter.setNewData(new ArrayList<>(series));
        }
    }

    private void downloadCurrentEpisode() {
        if (vodInfo == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
            Toast.makeText(this, "请选择剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (vodInfo.playIndex < 0 || vodInfo.playIndex >= series.size()) {
            Toast.makeText(this, "请选择剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        VodInfo.VodSeries vodSeries = series.get(vodInfo.playIndex);
        if (TextUtils.isEmpty(vodSeries.url)) {
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadTask task = new DownloadTask(
                vodInfo.id,
                vodInfo.name,
                vodSeries.url,
                sourceKey,
                vodInfo.pic,
                vodSeries.name
        );
        DownloadManager.get().addTask(task);
        Toast.makeText(this, "已添加到下载队列: " + vodSeries.name, Toast.LENGTH_SHORT).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefresh(RefreshEvent event) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}