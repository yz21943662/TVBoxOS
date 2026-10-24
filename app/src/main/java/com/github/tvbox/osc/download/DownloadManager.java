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
    private final java.util.Map<String, java.util.concurrent.Future<?>> activeFutures = new java.util.concurrent.ConcurrentHashMap<>();

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
        // 删除已下载文件
        String path = getDownloadPath(task);
        if (path != null) {
            try { new File(path).delete(); } catch (Exception ignored) {}
        }
    }

    public void retryTask(DownloadTask task) {
        if (task.status == DownloadTask.STATUS_FAILED || task.status == DownloadTask.STATUS_PAUSED) {
            task.downloadedSize = 0;
            task.progress = 0;
            // 删除不完整的文件
            String path = getDownloadPath(task);
            if (path != null) {
                try {
                    File f = new File(path);
                    if (f.exists() && f.length() < task.totalSize) f.delete();
                } catch (Exception ignored) {}
            }
            startDownload(task);
        }
    }

    public void pauseTask(DownloadTask task) {
        if (task.status == DownloadTask.STATUS_DOWNLOADING || task.status == DownloadTask.STATUS_PENDING) {
            task.status = DownloadTask.STATUS_PAUSED;
            // 取消正在执行的任务
            java.util.concurrent.Future<?> future = activeFutures.remove(task.url);
            if (future != null) {
                future.cancel(true);
            }
            notifyTaskChanged(task);
        }
    }

    public void resumeTask(DownloadTask task) {
        if (task.status == DownloadTask.STATUS_PAUSED) {
            // 部分下载的文件不完整，删掉从头下载
            String path = getDownloadPath(task);
            if (path != null) {
                try { new File(path).delete(); } catch (Exception ignored) {}
            }
            task.downloadedSize = 0;
            task.progress = 0;
            startDownload(task);
        }
    }

    public String getDownloadDir() {
        String cachePath = FileUtils.getCachePath();
        File dir = new File(cachePath, "downloads");
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
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
        // 先取消旧任务
        java.util.concurrent.Future<?> oldFuture = activeFutures.remove(task.url);
        if (oldFuture != null) oldFuture.cancel(true);

        task.status = DownloadTask.STATUS_DOWNLOADING;
        notifyTaskChanged(task);

        java.util.concurrent.Future<?> future = executor.submit(() -> {
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
                // 被暂停的不算失败
                if (task.status != DownloadTask.STATUS_PAUSED) {
                    task.status = DownloadTask.STATUS_FAILED;
                    notifyTaskChanged(task);
                }
            } finally {
                activeFutures.remove(task.url);
            }
        });
        activeFutures.put(task.url, future);
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