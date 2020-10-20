package com.chinamobile.bsdiff;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.chinamobile.upload.UploadFile;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.request.base.Request;

import org.apache.cordova.CallbackContext;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;


/**
 * Created by liangzhongtai on 2019/9/30.
 * 断点下载任务
 */

public class DownloadBreakPoint {
    private CallbackContext callBack;
    private Call call;
    private long max = 1;
    private final static long MAX_NO = 666;
    public final static int NORMAL_LENGTH = 10240;
    private long lastTime = 0;
    public int interval = 100;
    public int uploadType;
    public boolean isCancel;
    /**
     * 断点续传
     * */
    public void downloadBreakPoint(String url, String fileName, String dirName, String tickets,
                                   CallbackContext callBack, DownloadBreakPointListener listener) {
        // 检查是否已经创建目录
        String dirPath = Environment.getExternalStorageDirectory()
                + (TextUtils.isEmpty(dirName) ? "" : ("/" + dirName));
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String urlOri = url;
        if (!TextUtils.isEmpty(tickets)) {
            if (url.contains("?")) {
                url = url + "&tickets=" + tickets;
            } else {
                url = url + "?tickets=" + tickets;
            }
        }
        this.callBack = callBack;
        long start = getFileStart(dirName, fileName);
        final long startsPoint = start > 0 ? start - 1 : start;
        Log.d(BsdiffUpdate.TAG, "初始化_startsPoint=" + startsPoint);
        Log.d(BsdiffUpdate.TAG, "最终_url=" + url);
        okhttp3.Request request;
        if (TextUtils.isEmpty(tickets)) {
            request = new okhttp3.Request.Builder()
                .url(url)
                .tag(urlOri)
                .header("RANGE", "bytes=" + startsPoint + "-")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", " Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36")
                .build();
        } else {
            request = new okhttp3.Request.Builder()
                .url(url)
                .tag(urlOri)
                //.header("Cookie", "CASTGC=" + tickets)
                .header("RANGE", "bytes=" + startsPoint + "-")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", " Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36")
                .build();
            Log.d(BsdiffUpdate.TAG, "设置cookie");
        }
        // 重写ResponseBody监听请求
        Interceptor interceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Response originalResponse = chain.proceed(chain.request());

                // 打印响应头
                // 不能直接使用response.body().string()的方式输出日志
                ResponseBody responseBody = originalResponse.peekBody(1024 * 1024);
                Log.d(BsdiffUpdate.TAG, String.format("接收响应头: [%s] %n返回json:【%s】%n%s",
                        originalResponse.request().url(),
                        responseBody.string(),
                        originalResponse.headers()));

                return originalResponse
                        .newBuilder()
                        .body(new DownloadResponseBody(originalResponse, startsPoint, listener))
                        .build();
            }
        };

        OkHttpClient.Builder client = new OkHttpClient.Builder().addNetworkInterceptor(interceptor);
        // 绕开证书
        /*try {
            setSSL(dlOkhttp);
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        Log.d(BsdiffUpdate.TAG, "发起请求");
        // 发起请求
        call = client.build().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(BsdiffUpdate.TAG, "call=" + call.request().header("Cookie"));
                Log.d(BsdiffUpdate.TAG, "e=" + e.toString());
                listener.fail(uploadType, -1, "请求失败，未响应",  callBack);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                long length = response.body().contentLength();
                int responseCode = response.code();
                Log.d(BsdiffUpdate.TAG, "服务器响应_responseCode=" + responseCode);
                Log.d(BsdiffUpdate.TAG, "服务器响应_length=" + length);
                Log.d(BsdiffUpdate.TAG, "服务器响应_startsPoint=" + startsPoint);
                Log.d(BsdiffUpdate.TAG, "服务器响应1");
                if (responseCode < 200 || responseCode >= 300) {
                    listener.fail(uploadType, -1, "下载链接中断",  callBack);
                    return;
                }
                Log.d(BsdiffUpdate.TAG, "服务器响应2");
                long sp = startsPoint;
                // 如果当前的文件大小大于文件长度，则下载的安装包有误
                if (length > NORMAL_LENGTH && length < startsPoint) {
                    Log.d(BsdiffUpdate.TAG, "服务器响应3");
                    File file = getFile(dirName, fileName);
                    // 删除原文件，停止下载
                    if (file.exists()) {
                        file.delete();
                    }
                    listener.fail(uploadType, -1, "下载文件错误",  callBack);
                    return;
                }
                // 可以断点
                if (length == 0 && sp > 0) {
                    Log.d(BsdiffUpdate.TAG, "服务器响应4");
                    // 说明文件已经下载完
                    listener.complete(uploadType, String.valueOf(getFile(dirName, fileName).getAbsoluteFile()), callBack);
                    return;
                // 无法断点，
                } else if (length == -1 ) {
                    Log.d(BsdiffUpdate.TAG, "服务器响应5");
                    sp = 0;
                    // 移除原文件，从头开始下载
                    File file = new File(dir, fileName);
                    if (file.exists()) {
                        file.delete();
                    }
                }
                Log.d(BsdiffUpdate.TAG, "服务器响应6");
                max = length + sp;
                listener.start(uploadType, length + sp, length, callBack);
                // 保存文件到本地
                InputStream is = null;
                RandomAccessFile randomAccessFile = null;
                BufferedInputStream bis = null;

                byte[] buff = new byte[2048];
                int len;
                try {
                    is = response.body().byteStream();
                    bis = new BufferedInputStream(is);

                    File file = getFile(dirName, fileName);
                    // 随机访问文件，可以指定断点续传的起始位置
                    randomAccessFile =  new RandomAccessFile(file, "rwd");
                    randomAccessFile.seek (sp);
                    while ((len = bis.read(buff)) != -1) {
                        randomAccessFile.write(buff, 0, len);
                    }
                    // 下载完成
                    listener.complete(uploadType, String.valueOf(file.getAbsoluteFile()), callBack);
                } catch (Exception e) {
                    e.printStackTrace();

                    // 下载失败
                    listener.loadfail(uploadType, "下载已中断", callBack);
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                        if (bis != null){
                            bis.close();
                        }
                        if (randomAccessFile != null) {
                            randomAccessFile.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        });
    }

    /**
     * 非断点续传
     * */
    public void download(String url, String fileName, String dirName, boolean useStream,
                         String tickets, CallbackContext callBack,
                         DownloadBreakPointListener listener) {
        String dir = Environment.getExternalStorageDirectory()
                + (TextUtils.isEmpty(dirName) ? "" : ("/" + dirName));
        // 如果存在，先删除之前的文件
        File file = new File(dir, fileName);
        if (file.exists()) {
            file.delete();
        }
        String urlOri = url;
        if (!TextUtils.isEmpty(tickets)) {
            if (url.contains("?")) {
                url = url + "&tickets=" + tickets;
            } else {
                url = url + "?tickets=" + tickets;
            }
        }
        // 使用流下载的方式
        if (useStream) {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                URL urlFormat = new URL(url);
                URLConnection connection = urlFormat.openConnection();
                InputStream inputStream = connection.getInputStream();
                int length;
                byte[] bytes = new byte[1024];
                while ((length = inputStream.read(bytes)) != -1) {
                    fileOutputStream.write(bytes, 0, length);
                }
                fileOutputStream.close();
                inputStream.close();
                Log.d(BsdiffUpdate.TAG, "流下载结束");
            } catch (IOException e) {
                Log.d(BsdiffUpdate.TAG, "流下载失败");
            }
        } else {
            OkGo.<File>get(url).tag(urlOri).execute(new FileCallback(dir, fileName) {
                @Override
                public void onStart(Request<File, ? extends Request> request) {
                    super.onStart(request);
                    Log.d(BsdiffUpdate.TAG, "开始下载文件");
                    listener.start(uploadType, 0, 0, callBack);
                }

                @Override
                public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                    Log.d(BsdiffUpdate.TAG, "下载文件成功length=" + response.body().length());
                    // mBasePath=response.body().getAbsolutePath();
                    // plugin.sendUpdateResult(callbackContext, uploadType, UploadFile.DOWNLOAD_SUCCESS, "保存成功");
                    listener.complete(uploadType, dir + "/" + fileName, callBack);
                }

                @Override
                public void onFinish() {
                    super.onFinish();
                    Log.d(BsdiffUpdate.TAG, "下载文件完成");
                    // SPUtils.getInstance().put("localPath", mBasePath);
                }

                @Override
                public void onError(com.lzy.okgo.model.Response<File> response) {
                    super.onError(response);
                    Log.d(BsdiffUpdate.TAG, "下载文件出错error=" + response.message());
                    // plugin.sendUpdateResult(callbackContext, uploadType, UploadFile.DOWNLOAD_FAILE, "下载失败");
                    listener.loadfail(uploadType, "下载已中断", callBack);
                }

                @Override
                public void downloadProgress(Progress progress) {
                    super.downloadProgress(progress);
                    float dLProgress = progress.fraction;
                    Log.d(BsdiffUpdate.TAG, "文件下载的进度=" + dLProgress);
                    listener.loading(uploadType, dLProgress, callBack);
                }
            });
        }
    }

    private File getFile(String dirName, String fileName) {
        String root = Environment.getExternalStorageDirectory() + "/";
        File file = new File(TextUtils.isEmpty(dirName) ? root : (root + "/" + dirName), fileName);
        return file;
    }

    private long getFileStart(String dirName, String fileName){
        String root = Environment.getExternalStorageDirectory() + "";
        File file = new File(TextUtils.isEmpty(dirName) ? root : (root + "/" + dirName), fileName);
        return file.length();
    }

    /**
     * 取消下载
     * */
    public void cancel() {
        isCancel = true;
        if (call == null || call.isCanceled()) {
            return;
        }
        call.cancel();
    }

    /**
     * 重写的ResponseBody内部类
     * */
    public class DownloadResponseBody extends ResponseBody {

        private Response originalResponse;
        private DownloadBreakPointListener downloadListener;
        private long oldPoint = 0;

        public DownloadResponseBody(Response originalResponse, long startsPoint,
                                    DownloadBreakPointListener downloadListener) {
            this.originalResponse = originalResponse;
            this.downloadListener = downloadListener;
            this.oldPoint = startsPoint;
        }

        @Override
        public MediaType contentType() {
            return originalResponse.body().contentType();
        }

        @Override
        public long contentLength() {
            return originalResponse.body().contentLength();
        }

        @Override
        public BufferedSource source() {
            return Okio.buffer(new ForwardingSource(originalResponse.body().source()) {
                private long bytesReaded = 0;
                @Override
                public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    bytesReaded += bytesRead == -1 ? 0 : bytesRead;
                    Log.d(BsdiffUpdate.TAG, "文件oldPoint=" + oldPoint);
                    Log.d(BsdiffUpdate.TAG, "文件bytesReaded=" + bytesReaded);
                    long timeNow = System.currentTimeMillis();
                    if (downloadListener != null && timeNow - lastTime > interval) {
                        lastTime = timeNow;
                        //downloadListener.loading((int) ((bytesReaded+oldPoint)/(1024)), callBack);
                        downloadListener.loading(uploadType, max == 0 ? 0 : (float) (bytesReaded + oldPoint) / max, callBack);
                    }
                    return bytesRead;
                }
            });
        }

    }
}
