package com.github.tvbox.osc.download;

import java.io.Serializable;

public class DownloadTask implements Serializable {
    public String id;
    public String name;
    public String url;
    public String sourceKey;
    public String pic;
    public String episodeName;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_PAUSED = 4;

    public int status = STATUS_PENDING;
    public long totalSize = 0;
    public long downloadedSize = 0;
    public int progress = 0;
    public long createTime = System.currentTimeMillis();

    public DownloadTask() {}

    public DownloadTask(String id, String name, String url, String sourceKey, String pic, String episodeName) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.sourceKey = sourceKey;
        this.pic = pic;
        this.episodeName = episodeName;
    }

    public String getStatusText() {
        switch (status) {
            case STATUS_PENDING: return "等待中";
            case STATUS_DOWNLOADING: return "下载中";
            case STATUS_COMPLETED: return "已完成";
            case STATUS_FAILED: return "失败";
            case STATUS_PAUSED: return "已暂停";
            default: return "未知";
        }
    }
}