package com.github.tvbox.osc.download;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.MD5;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static DownloadManager instance;
    private final List<DownloadTask> taskList = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<DownloadListener> listeners = new ArrayList<>();

    public interface DownloadListener {
        void onTaskChanged(DownloadTask task);
        void onTaskCompleted(DownloadTask task);
    }

    private DownloadManager() {
        client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public static synchronized DownloadManager get() {
        if (instance == null) {
            instance = new DownloadManager();
        }
        return instance;
    }

    public void addListener(DownloadListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    private void notifyTaskChanged(DownloadTask task) {
        mainHandler.post(() -> {
            for (DownloadListener l : listeners) {
                l.onTaskChanged(task);
            }
        });
    }

    private void notifyTaskCompleted(DownloadTask task) {
        mainHandler.post(() -> {
            for (DownloadListener l : listeners) {
                l.onTaskCompleted(task);
            }
        });
    }

    public List<DownloadTask> getTaskList() {
        synchronized (taskList) {
            List<DownloadTask> copy = new ArrayList<>(taskList);
            Collections.sort(copy, (a, b) -> Long.compare(b.createTime, a.createTime));
            return copy;
        }
    }

    public void addTask(DownloadTask task) {
        synchronized (taskList) {
            // check duplicate
            for (DownloadTask t : taskList) {
                if (t.url != null && t.url.equals(task.url)) {
                    return;
                }
            }
            taskList.add(task);
            notifyTaskChanged(task);
        }
        startDownload(task);
    }

    public void removeTask(DownloadTask task) {
        synchronized (taskList) {
            taskList.remove(task);
        }
    }

    public void removeCompleted() {
        synchronized (taskList) {
            List<DownloadTask> toRemove = new ArrayList<>();
            for (DownloadTask t : taskList) {
                if (t.status == DownloadTask.STATUS_COMPLETED) {
                    toRemove.add(t);
                }
            }
            taskList.removeAll(toRemove);
        }
    }

    private void startDownload(DownloadTask task) {
        task.status = DownloadTask.STATUS_DOWNLOADING;
        notifyTaskChanged(task);

        executor.execute(() -> {
            try {
                String cachePath = FileUtils.getCachePath();
                File downloadDir = new File(cachePath, "downloads");
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String fileName = task.name + "_" + task.episodeName;
                String ext = ".mp4";
                if (task.url.contains(".m3u8")) {
                    ext = ".m3u8";
                } else if (task.url.contains(".ts")) {
                    ext = ".ts";
                } else if (task.url.contains(".mp4")) {
                    ext = ".mp4";
                }
                File destFile = new File(downloadDir, MD5.encode(task.url) + ext);

                // if file already exists, mark as completed
                if (destFile.exists() && destFile.length() > 0) {
                    task.status = DownloadTask.STATUS_COMPLETED;
                    task.totalSize = destFile.length();
                    task.downloadedSize = destFile.length();
                    task.progress = 100;
                    notifyTaskChanged(task);
                    notifyTaskCompleted(task);
                    return;
                }

                Request request = new Request.Builder()
                        .url(task.url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    task.status = DownloadTask.STATUS_FAILED;
                    notifyTaskChanged(task);
                    return;
                }

                task.totalSize = response.body().contentLength();
                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(destFile);

                byte[] buffer = new byte[8192];
                int len;
                long downloaded = 0;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;
                    task.downloadedSize = downloaded;
                    if (task.totalSize > 0) {
                        task.progress = (int) (downloaded * 100 / task.totalSize);
                    }
                    notifyTaskChanged(task);
                }

                fos.close();
                is.close();
                response.close();

                task.status = DownloadTask.STATUS_COMPLETED;
                task.progress = 100;
                task.downloadedSize = task.totalSize;
                notifyTaskChanged(task);
                notifyTaskCompleted(task);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + task.url, e);
                task.status = DownloadTask.STATUS_FAILED;
                notifyTaskChanged(task);
            }
        });
    }

    public String getDownloadPath(DownloadTask task) {
        String cachePath = FileUtils.getCachePath();
        File downloadDir = new File(cachePath, "downloads");
        String ext = ".mp4";
        if (task.url != null && task.url.contains(".m3u8")) ext = ".m3u8";
        else if (task.url != null && task.url.contains(".ts")) ext = ".ts";
        File destFile = new File(downloadDir, MD5.encode(task.url) + ext);
        if (destFile.exists()) return destFile.getAbsolutePath();
        return null;
    }
}