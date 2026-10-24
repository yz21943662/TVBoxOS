package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadActivity extends BaseActivity {
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private SourceViewModel sourceViewModel;
    private String searchTitle = "";
    private ExecutorService searchExecutor;
    private final List<Movie.Video> allSearchResults = new ArrayList<>();
    private int totalSearchCount = 0;
    private int completedSearchCount = 0;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_download;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);

        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvClear = findViewById(R.id.tvClear);
        keyboard = findViewById(R.id.keyboard);
        mGridView = findViewById(R.id.mGridView);
        mGridViewWord = findViewById(R.id.mGridViewWord);

        mGridView.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    cancelSearch();
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    bundle.putString("picture", video.pic);
                    jumpActivity(DownloadDetailActivity.class, bundle);
                }
            }
        });

        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this, V7LinearLayoutManager.HORIZONTAL, false));
        wordAdapter = new PinyinAdapter();
        mGridViewWord.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(wordAdapter.getItem(position));
            }
        });
        loadHotWords();

        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos == 1) {
                    String txt = etSearch.getText().toString();
                    if (!TextUtils.isEmpty(txt)) {
                        etSearch.setText(txt.substring(0, txt.length() - 1));
                    }
                } else if (pos >= 2) {
                    etSearch.append(key);
                }
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    startSearch(wd);
                }
            }
        });

        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
                allSearchResults.clear();
                searchAdapter.setNewData(new ArrayList<>());
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String wd = etSearch.getText().toString().trim();
            if (!TextUtils.isEmpty(wd)) {
                startSearch(wd);
            }
            return true;
        });
    }

    private void loadHotWords() {
        ArrayList<String> hots = new ArrayList<>();
        hots.add("热门");
        hots.add("电视剧");
        hots.add("电影");
        hots.add("综艺");
        hots.add("动漫");
        hots.add("纪录片");
        hots.add("动作");
        hots.add("喜剧");
        hots.add("爱情");
        hots.add("科幻");
        wordAdapter.setNewData(hots);
    }

    private void startSearch(String title) {
        cancelSearch();
        showLoading();
        etSearch.setText(title);
        HistoryHelper.setSearchHistory(title);
        this.searchTitle = title;
        allSearchResults.clear();
        searchAdapter.setNewData(new ArrayList<>());

        List<SourceBean> sources = ApiConfig.get().getSourceBeanList();
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        List<SourceBean> searchList = new ArrayList<>(sources);
        searchList.remove(home);
        searchList.add(0, home);

        List<SourceBean> filtered = new ArrayList<>();
        for (SourceBean bean : searchList) {
            if (bean.isSearchable()) {
                filtered.add(bean);
            }
        }

        if (filtered.isEmpty()) {
            Toast.makeText(this, "没有可用的搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }

        totalSearchCount = filtered.size();
        completedSearchCount = 0;

        searchExecutor = Executors.newFixedThreadPool(6);
        for (SourceBean bean : filtered) {
            String key = bean.getKey();
            searchExecutor.execute(() -> sourceViewModel.getSearch(key, title));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSearchResult(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT && event.data instanceof AbsXml) {
            AbsXml data = (AbsXml) event.data;
            if (data.movie != null && data.movie.videoList != null) {
                synchronized (allSearchResults) {
                    for (Movie.Video v : data.movie.videoList) {
                        if (v.sourceKey == null) v.sourceKey = data.sourceKey;
                        allSearchResults.add(v);
                    }
                }
            }
            completedSearchCount++;
            checkSearchAllDone();
        }
    }

    private void checkSearchAllDone() {
        if (completedSearchCount >= totalSearchCount) {
            if (allSearchResults.isEmpty()) {
                Toast.makeText(this, "未找到相关结果", Toast.LENGTH_SHORT).show();
                showEmpty();
            } else {
                showSuccess();
                searchAdapter.setNewData(new ArrayList<>(allSearchResults));
            }
        }
    }

    private void cancelSearch() {
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdownNow();
        }
        allSearchResults.clear();
        totalSearchCount = 0;
        completedSearchCount = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        cancelSearch();
    }
}