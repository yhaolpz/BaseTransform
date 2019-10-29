package com.wyh.test;

import android.util.Log;

import androidx.annotation.NonNull;

import com.test.Aop;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class Test {

    @Aop
    public void test() {
        Log.d("Test", "test");
    }

    public void test2() {
        Log.d("Test", "test2");
    }

    @NonNull
    public void test3() {
        Log.d("Test", "test2");
    }
}
