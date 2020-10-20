package com.chinamobile.bsdiff;

/**
 * Created by liangzhongtai on 2020/7/9.
 */

public class BsdiffJNI {

    static {
        System.loadLibrary("native-lib");
    }

    public native void bsPatchs(String oldApk, String patch, String output);
}
