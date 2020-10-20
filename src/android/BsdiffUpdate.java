package com.chinamobile.bsdiff;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;


import com.chinamobile.upload.DownloadFileUtil;
import com.chinamobile.upload.DownloadFloatingService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;


/**
 * Created by liangzhongtai on 2020/7/9.
 */

public class BsdiffUpdate extends CordovaPlugin {
    public final static String TAG                   = "BsdiffUpdate_Plugin";
    public final static String URL                   = "url";
    public final static String FILE_NAME             = "fileName";
    public final static String DIR_NAME              = "dirName";
    public final static String TICKETS               = "tickets";
    public final static String VERSION               = "version";
    public final static String FILE_NAME_TASK_BSDIFF = "update_bsdiff";
    private static int HANDLER_INSTALL = 1;
    private static int HANDLER_DELAY_CHECK = 2;
    private static long DELAY_TIME = 10000;
    // 下载增量包
    public final static int BSDIFF_DOWNLOAD_PACKAGE = 0;
    // 检查增量包
    public final static int CHECK_DOWNLOAD_PACKAGE  = 1;
    // 删除增量更新记录
    public final static int DELETE_DOWNLOAD_RECORD = 2;
    // 权限设置
    public final static int RESULTCODE_PERMISSION       = 567010;
    public final static int RESULTCODE_OVERLAY_WINDOW   = 562344;
    public final static int RESULTCODE_INSTALL          = 562378;


    public JSONArray args;
    private CallbackContext callbackContext;
    private String state;
    private String outPath;
    private static long maxNow;
    private static String urlFinal;
    private static String fileName;
    private static String dirName;
    private static String version;
    private static String tickets;
    private static String envi;
    private static boolean hasStart;
    private static boolean isLoading;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
        this.webView = webView;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        Log.d(TAG,"执行方法BsdiffUpdate");
        Log.d(TAG,"length="+args.length());
        if ("coolMethod".equals(action)) {
            // 权限
            try {
                if(!PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    ||!PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ||!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)) {
                    this.args = args;
                    this.callbackContext = callbackContext;
                    PermissionHelper.requestPermissions(this,RESULTCODE_PERMISSION,new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_NETWORK_STATE});
                } else {
                    startWork(args, callbackContext);
                }
            } catch (Exception e){
                //权限异常
                callbackContext.error("增量更新功能异常");
                return true;
            }
            return true;
        }
        return super.execute(action, args, callbackContext);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.error("缺少内部存储读写权限, 无法使用增量更新功能");
                return;
            }
        }
        switch (requestCode) {
            case RESULTCODE_PERMISSION:
                startWork(args, callbackContext);
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.d(TAG, "权限检查返回_onActivityResult=" + requestCode);
        switch (requestCode) {
            case RESULTCODE_INSTALL:
                /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    boolean installAllowed = cordova.getActivity().getApplicationContext().
                            getPackageManager().canRequestPackageInstalls();
                    Log.d(TAG, "----------installAllowed=" + installAllowed);
                    if (!installAllowed) {
                        return;
                    }
                }*/
                // 授予未知应用安装权限，启动安装
                Log.d(TAG, "----------outPath=" + outPath);
                if (TextUtils.isEmpty(outPath)) {
                    return;
                }
                File file = new File(outPath);
                Log.d(TAG, "----------file=" + file.exists());
                if (!file.exists()) {
                    return;
                }
                Log.d(TAG, "----------install");
                BsdiffUtil.openFile(cordova.getActivity(), file);
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    /**
     * 处理不同的接口
     * */
    private void startWork(JSONArray args, CallbackContext callBack) {
        try {
            int bsdiffType = args.getInt(0);
            // 断点续传下载增量包
            if (bsdiffType == BSDIFF_DOWNLOAD_PACKAGE) {
                Log.d(TAG, "下载增量更新包");
                String url          = args.getString(1);
                String urlOri       = url;
                version             = args.getString(2);
                tickets             = args.getString(3);
                envi                = args.getString(4);
                String versionNow   = getNowVersion();
                if (TextUtils.isEmpty(version) || version.compareTo(versionNow) <= 0) {
                    return;
                }
                fileName            = "app_" + envi + "_" + versionNow + ".patch";
                url                 = url + fileName;
                urlFinal            = url;
                dirName             = "";
                Log.d(TAG, "保存下载记录_url=" + url + "_fileName=" + fileName);
                // 先删除旧文件
                File file = getFile(dirName, fileName);
                if (file.exists()) {
                    file.delete();
                }
                saveTask(urlOri);
                download();
                if (!hasStart) {
                    hasStart = true;
                    Message msg = new Message();
                    msg.what = HANDLER_DELAY_CHECK;
                    handler.sendMessageDelayed(msg, DELAY_TIME);
                }
            // 检查增量包下载任务
            } else if (bsdiffType == CHECK_DOWNLOAD_PACKAGE) {
                Log.d(TAG, "检查更新下载任务");
                // install();
                tickets = args.getString(1);
                // 已启动下载，不再检查
                if (isLoading) {
                    return;
                }
                JSONObject obj = DownloadFileUtil.readObject(FILE_NAME_TASK_BSDIFF, "");
                if (obj == null) {
                    return;
                }
                String url       = obj.getString(URL);

                fileName         = obj.getString(FILE_NAME);
                dirName          = obj.getString(DIR_NAME);
                if (TextUtils.isEmpty(tickets)) {
                    tickets      = obj.getString(TICKETS);
                }
                // 格式化更新版本
                version          = obj.getString(VERSION);
                // 获取本地版本
                try {
                    String versionNow = getNowVersion();
                    // 比较更新版本和本地版本
                    if (TextUtils.isEmpty(version) ||
                        version.compareTo(versionNow) <= 0) {
                        return;
                    }
                    url      = url + fileName;
                    urlFinal = url;
                    Log.d(TAG, "检查更新记录url=" + url);
                    // 启动断点下载更新
                    // 有悬浮窗权限开启服务绑定
                    if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(cordova.getActivity())) {
                        Log.d(TAG, "启动断点下载更新");
                        // 启动下载
                        download();
                        if (!hasStart) {
                            hasStart = true;
                            Message msg = new Message();
                            msg.what = HANDLER_DELAY_CHECK;
                            handler.sendMessageDelayed(msg, DELAY_TIME);
                        }
                        // 没有悬浮窗权限，提示悬浮窗权限
                    } else {
                        Log.d(TAG, "启动弹窗权限页面");
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            Uri packageURI = Uri.parse("package:" + cordova.getActivity().getPackageName());
                            intent.setData(packageURI);
                            cordova.setActivityResultCallback(this);
                            cordova.getActivity().startActivityForResult(intent, RESULTCODE_OVERLAY_WINDOW);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            // 删除增量更新记录
            } else if (bsdiffType == DELETE_DOWNLOAD_RECORD) {
                JSONObject obj = new JSONObject();
                BsdiffUtil.writeObject(FILE_NAME_TASK_BSDIFF, "", obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存下载任务
     * */
    private void saveTask(String urlOri) {
        // 保存下载记录
        JSONObject obj = new JSONObject();
        try {
            obj.put(URL, urlOri);
            obj.put(FILE_NAME, fileName);
            obj.put(DIR_NAME, dirName);
            obj.put(TICKETS, tickets);
            obj.put(VERSION, version);
        } catch (Exception e) {
            e.printStackTrace();
        }
        BsdiffUtil.writeObject(FILE_NAME_TASK_BSDIFF, "", obj);
    }

    /**
     * 启动断点下载
     * */
    private void download() {
        try {
            // 启动断点续传
            DownloadBreakPointManager.getInstance().
                downLoader(BSDIFF_DOWNLOAD_PACKAGE, urlFinal, fileName, dirName, true,
                        false, tickets, callbackContext,
                    new DownloadBreakPointListener() {
                        @Override
                        public void start(int uploadType, long max, long maxService, CallbackContext callBack) {
                            // 文件总大小
                            Log.d(BsdiffUpdate.TAG, fileName + "_文件总大小max=" + max);
                            state = "已就绪";
                            maxNow = max;
                            isLoading = true;
                        }

                        @Override
                        public void loading(int uploadType, float progress, CallbackContext callBack) {
                            // 下载进度
                            Log.d(BsdiffUpdate.TAG, fileName + "_下载进度progress=" + progress);
                            state = "下载中";
                            isLoading = true;
                        }

                        @Override
                        public void complete(int uploadType, String path, CallbackContext callBack) {
                            // 移除任务
                            DownloadBreakPointManager.getInstance().remove(urlFinal, fileName, callBack);
                            state = "点击安装";
                            isLoading = false;
                            File file = getFile(dirName, fileName);
                            if (file.length() <  maxNow || file.length() < DownloadBreakPoint.NORMAL_LENGTH) {
                                state = "已中断";
                                isLoading = false;
                                return;
                            }
                            // 下载完成
                            Log.d(BsdiffUpdate.TAG, fileName + "_下载完成complete=" + path);
                            Message msg = new Message();
                            msg.what = HANDLER_INSTALL;
                            handler.sendMessage(msg);
                        }

                        @Override
                        public void fail(int uploadType, int code, String message, CallbackContext callBack) {
                            // 请求失败
                            Log.d(BsdiffUpdate.TAG, fileName + "_请求失败fail=" + message);
                            state = "下载失败";
                            isLoading = false;
                        }

                        @Override
                        public void loadfail(int uploadType, String message, CallbackContext callBack) {
                            // 下载异常
                            Log.d(BsdiffUpdate.TAG, fileName + "_下载异常loadfail=" + message);
                            state = "已中断";
                            isLoading = false;
                        }
                    });
            Log.d(TAG, "启动增量包断点下载");
        } catch (Exception e) {
            e.printStackTrace();
            state = "下载失败";
        }
    }

    /**
     * 检查和安装增量更新包
     * */
    private void install() {
        // 下载完成，检查是否存在增量包
        File file = getFile(dirName, fileName);
        if (!file.exists() || file.length() < DownloadBreakPoint.NORMAL_LENGTH) {
            Log.d(TAG, "差分包不存在_length=" + file.length());
            return;
        }

        // 弹窗提示安装新的apk
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                // 合并增量包
                String patch =  file.getAbsolutePath();
                Log.d(TAG, "生成差分包1——patch=" + patch);
                String oldApk = cordova.getActivity().getApplicationInfo().sourceDir;
                Log.d(TAG, "生成差分包2——oldApk=" + oldApk);
                File outputFile = getFile(dirName, fileName.replace("patch", "apk"));
                String output = outputFile.getAbsolutePath();
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                Log.d(TAG, "生成差分包3——output=" + output);
                // 检查是否存在合并的apk
                BsdiffJNI jni = new BsdiffJNI();
                Log.d(TAG, "生成差分包4——jni=" + jni);
                jni.bsPatchs(oldApk, patch, output);
                Log.d(TAG, "生成差分包5——合并成功");
                return new File(output);
            }

            @Override
            protected void onPostExecute(File file) {
                super.onPostExecute(file);
                Log.d(TAG, "生成差分包6——file=" + file);
                // 安装新apk
                if (file != null) {
                    if (!file.exists()) {
                        Log.d(TAG, "合并的安装包不存在");
                        return;
                    }
                    Activity activity = BsdiffUpdate.this.cordova.getActivity();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        boolean installAllowed = activity.getApplicationContext().getPackageManager().canRequestPackageInstalls();
                        Log.d(TAG, "生成差分包7——installAllowed=" + installAllowed);
                        if (!installAllowed) {
                            outPath = file.getAbsolutePath();
                            Log.d(TAG, "安装未知应用");
                            // 将用户引导至安装未知应用界面。
                            Intent intent = new Intent();
                            //获取当前apk包URI，并设置到intent中（这一步设置，可让“未知应用权限设置界面”只显示当前应用的设置项）
                            Uri packageURI = Uri.parse("package:" + activity.getApplicationContext().getPackageName());
                            intent.setData(packageURI);
                            //设置不同版本跳转未知应用的动作
                            if (Build.VERSION.SDK_INT >= 26) {
                                intent.setAction(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            } else {
                                intent.setAction(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            BsdiffUpdate.this.cordova.setActivityResultCallback(BsdiffUpdate.this);
                            activity.startActivityForResult(intent, RESULTCODE_INSTALL);
                            return;
                        }
                    }
                    Log.d(TAG, "生成差分包8");
                    BsdiffUtil.openFile(activity, file);
                } else {
                    Log.d(TAG, "合并的安装包不存在");
                }
            }
        }.execute();
    }

    /**
     * 获取当前客户端版本
     * */
    private String getNowVersion() {
        String versionName = "";
        try {
            String pkName = cordova.getActivity().getApplication().getPackageName();
            versionName = cordova.getActivity().getApplicationContext().getPackageManager().
                    getPackageInfo(pkName, 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return versionName;
    }

    /**
     * 获取文件
     * */
    private File getFile(String dirName, String fileName) {
        String root = Environment.getExternalStorageDirectory() + "/";
        File file = new File(TextUtils.isEmpty(dirName) ? root : (root + "/" + dirName), fileName);
        return file;
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == HANDLER_INSTALL) {
                // 安装
                install();
            } else if (msg.what == HANDLER_DELAY_CHECK) {
                if ("已中断".equals(state) || "下载失败".equals(state)) {
                    BsdiffUpdate.this.download();
                } else if ("点击安装".equals(state)) {
                    // 检查文件是否存在
                    File file = getFile(dirName, fileName);
                    long size = file.length();
                    if (!file.exists() && version.compareTo(getNowVersion()) > 0) {
                        // 不存在，重新下载
                        BsdiffUpdate.this.download();
                        return;
                    }

                    if  (size < DownloadBreakPoint.NORMAL_LENGTH ||
                        (size > DownloadBreakPoint.NORMAL_LENGTH &&
                        maxNow > DownloadBreakPoint.NORMAL_LENGTH &&
                        size > maxNow + 1)) {
                        try {
                            file.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // 文件下载大小错误，重新下载
                        BsdiffUpdate.this.download();
                        return;
                    }
                }
                Message message = new Message();
                message.what = HANDLER_DELAY_CHECK;
                handler.sendMessageDelayed(message, DELAY_TIME);
            }
        }
    };
}
