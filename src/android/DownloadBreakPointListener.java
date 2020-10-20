package com.chinamobile.bsdiff;

import org.apache.cordova.CallbackContext;


/**
 * Created by liangzhongtai on 2019/9/30.
 * 断点续传监听接口
 */

public interface DownloadBreakPointListener {
    /**
     *  开始下载
     */
    void start(int uploadType, long max, long maxService, CallbackContext callBack);
    /**
     *  正在下载
     */
    void loading(int uploadType, float progress, CallbackContext callBack);
    /**
     *  下载完成
     */
    void complete(int uploadType, String path, CallbackContext callBack);
    /**
     *  请求失败
     */
    void fail(int uploadType, int code, String message, CallbackContext callBack);
    /**
     *  下载过程中失败
     */
    void loadfail(int uploadType, String message, CallbackContext callBack);
}
