package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.Movie;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 搜索结果过滤：剔除明显无法播放的条目
 * 例如某些源未配置网盘时，会把错误提示当成影片名返回
 */
public class SearchResultFilter {

    /**
     * 错误提示类文本特征：命中任意一个即认为该条目不可播放
     */
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "没有配置|未配置|请先配置|请配置|配置网盘|网盘(未|没)|"
                    + "无法播放|不能播放|不可播放|暂无播放|没有播放|无播放地址|"
                    + "播放失败|加载失败|解析失败|获取失败|请求失败|接口错误|服务器错误|"
                    + "请登录|需要登录|会员专属|"
                    + "not\\s*found|error|forbidden|404|403",
            Pattern.CASE_INSENSITIVE);

    private SearchResultFilter() {
    }

    /**
     * 判断单条搜索结果是否可播放（可进入详情）
     */
    public static boolean isPlayable(Movie.Video video) {
        if (video == null) return false;
        if (TextUtils.isEmpty(video.id) || TextUtils.isEmpty(video.name)) return false;
        String name = video.name.trim();
        // 名称是纯错误提示（如"没有配置网盘"）时过滤掉
        if (ERROR_PATTERN.matcher(name).find()) return false;
        if (!TextUtils.isEmpty(video.note) && ERROR_PATTERN.matcher(video.note).find()
                && name.length() <= 20) {
            return false;
        }
        return true;
    }

    /**
     * 过滤列表，保留可播放条目
     */
    public static List<Movie.Video> filter(List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) return videos;
        for (int i = videos.size() - 1; i >= 0; i--) {
            if (!isPlayable(videos.get(i))) {
                videos.remove(i);
            }
        }
        return videos;
    }
}
