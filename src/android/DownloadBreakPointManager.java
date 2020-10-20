package com.chinamobile.bsdiff;

import android.text.TextUtils;
import android.util.Log;

import com.chinamobile.upload.UploadFile;

import org.apache.cordova.CallbackContext;

import java.util.HashMap;

/**
 * Created by liangzhongtai on 2019/9/30.
 * 断点下载管理器
 */

public class DownloadBreakPointManager {
    private volatile static DownloadBreakPointManager uniqueInstance;
    private HashMap<String, DownloadBreakPoint> downloadMap;

    private DownloadBreakPointManager() {
    }

    public static DownloadBreakPointManager getInstance() {
        if (uniqueInstance == null) {
            synchronized (DownloadBreakPointManager.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new DownloadBreakPointManager();
                }
            }
        }
        return uniqueInstance;
    }

    /**
     * 添加下载任务
     * */
    public void downLoader(int uploadType, String url, String fileName, String dirName,
                           boolean useBreakPoint, boolean useStream, String tickets,
                           CallbackContext callBack, DownloadBreakPointListener listener) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String urlKey = formatUrl(url);
        // 查看下载任务是否已存在
        DownloadBreakPoint download = downloadMap().get(urlKey);
        // 不存在下载任务，重新创建
        if (download == null) {
            download = new DownloadBreakPoint();
            downloadMap().put(urlKey, download);
        } else {
            download.cancel();
        }
        download.uploadType = uploadType;
        if (useBreakPoint) {
            Log.d(UploadFile.TAG, "使用断点续传");
            download.downloadBreakPoint(url, fileName, dirName, tickets, callBack, listener);
        } else {
            Log.d(UploadFile.TAG, "使用普通下载");
            download.download(url, fileName, dirName, useStream, tickets, callBack, listener);
        }
    }



    /**
     * 移除任务
     * */
    public  void remove(String url, String fileName, CallbackContext callBack) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String urlKey = formatUrl(url);
        downloadMap().remove(urlKey);
    }

    /**
     * 取消任务
     * */
    public void cancel(String url, CallbackContext callBack) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String urlKey = formatUrl(url);

        DownloadBreakPoint download = downloadMap().get(urlKey);
        if (download == null) {
            return;
        }
        download.cancel();
    }

    /**
     * 取消全部任务
     * */
    public void cancelAll() {
        for (String key : downloadMap().keySet()) {
            DownloadBreakPoint download = downloadMap().get(key);
            if (download != null) {
                download.cancel();
            }
        }
    }

    /**
     * 格式化url
     * */
    public String formatUrl(String url) {
        return url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
    }

    public HashMap<String, DownloadBreakPoint> downloadMap() {
        downloadMap = downloadMap == null ? new HashMap(40) : downloadMap;
        return downloadMap;
    }
}
