package com.wyh.test;

import android.util.Log;

import com.example.mylibrary.Aop;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class Test {

    @Aop
    public void test() {
        Log.d("Test", "test Aop");
    }

}
