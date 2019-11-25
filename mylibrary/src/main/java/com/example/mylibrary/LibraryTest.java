package com.example.mylibrary;

import android.util.Log;

/**
 * @author WangYingHao
 * @since 2019/11/21
 */
public class LibraryTest {

    @Aop
    public static void log() {
        Log.d("LibraryTest", "log: abcc");
    }
}
