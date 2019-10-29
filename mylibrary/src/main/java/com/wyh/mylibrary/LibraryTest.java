package com.wyh.mylibrary;

import android.util.Log;

import com.test.Aop;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class LibraryTest {


    @Aop
    public void test() {
        Log.d("LibraryTest", "test");
    }
}
