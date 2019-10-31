package com.wyh.test;

import android.util.Log;

import androidx.annotation.NonNull;

import com.test.Aop;
import com.test.Aop2;
import com.test.Mode;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class Test {

    @Aop
    public void test() {
        Log.d("Test", "test Aop");
    }

    @Aop2
    public void test2() {
        Mode mode = Mode.HIGH;
        Log.d("Test", "test2 Aop2");
    }

    @Aop
    @NonNull
    @Aop2
    public void test3() {
        Log.d("Test", "test2");
    }
}
