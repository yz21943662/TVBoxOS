package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.download.DownloadManager;
import com.github.tvbox.osc.download.DownloadTask;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.List;

public class DownloadManagerActivity extends BaseActivity implements DownloadManager.DownloadListener {
    private TvRecyclerView mGridView;
    private TextView tvEmpty;
    private TextView tvClearDone;
    private DownloadTaskAdapter adapter;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_download_manager;
    }

    @Override
    protected void init() {
        mGridView = findViewById(R.id.mGridView);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvClearDone = findViewById(R.id.tvClearDone);

        mGridView.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        adapter = new DownloadTaskAdapter(new DiffUtil.ItemCallback<DownloadTask>() {
            @Override
            public boolean areItemsTheSame(DownloadTask oldItem, DownloadTask newItem) {
                return oldItem.url != null && oldItem.url.equals(newItem.url);
            }

            @Override
            public boolean areContentsTheSame(DownloadTask oldItem, DownloadTask newItem) {
                return oldItem.status == newItem.status && oldItem.progress == newItem.progress;
            }
        });
        mGridView.setAdapter(adapter);

        tvClearDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                DownloadManager.get().removeCompleted();
                refreshList();
                Toast.makeText(DownloadManagerActivity.this, "已清除完成的下载任务", Toast.LENGTH_SHORT).show();
            }
        });

        DownloadManager.get().addListener(this);
        refreshList();
    }

    private void refreshList() {
        List<DownloadTask> tasks = DownloadManager.get().getTaskList();
        adapter.submitList(tasks);
        if (tasks.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            mGridView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            mGridView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onTaskChanged(DownloadTask task) {
        refreshList();
    }

    @Override
    public void onTaskCompleted(DownloadTask task) {
        Toast.makeText(this, "下载完成: " + task.name + " " + task.episodeName, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.get().removeListener(this);
    }

    private static class DownloadTaskAdapter extends ListAdapter<DownloadTask, DownloadTaskAdapter.ViewHolder> {
        protected DownloadTaskAdapter(DiffUtil.ItemCallback<DownloadTask> diffCallback) {
            super(diffCallback);
        }

        @Override
        public ViewHolder onCreateViewHolder(RecyclerView.ViewGroup parent, int viewType) {
            View view = View.inflate(parent.getContext(), R.layout.item_download_task, null);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DownloadTask task = getItem(position);
            holder.tvName.setText(task.name);
            holder.tvEpisode.setText(task.episodeName != null ? task.episodeName : "");
            holder.tvStatus.setText(task.getStatusText());
            holder.tvProgress.setText(task.progress + "%");

            holder.tvDelete.setOnClickListener(v -> {
                DownloadManager.get().removeTask(task);
            });

            holder.itemView.setOnClickListener(v -> {
                if (task.status == DownloadTask.STATUS_COMPLETED) {
                    String path = DownloadManager.get().getDownloadPath(task);
                    if (path != null) {
                        Toast.makeText(holder.itemView.getContext(), "文件已保存至: " + path, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvEpisode, tvProgress, tvStatus, tvDelete;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvTaskName);
                tvEpisode = itemView.findViewById(R.id.tvEpisodeName);
                tvProgress = itemView.findViewById(R.id.tvProgress);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvDelete = itemView.findViewById(R.id.tvDelete);
            }
        }
    }
}