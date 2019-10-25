package com.wyh.plugin;

import java.util.Arrays;

/**
 * Created by wyh on 2019/3/20.
 * 日志消息相关处理
 */
public class LogUtil {

    /**
     * 将实例转换为字符串
     *
     * @param obj 实例，可直接格式数组、集合类型输出
     * @return 转换后的字符串
     */
    public static String toString( Object obj) {
        if (obj == null) {
            return "null";
        }
        if (!obj.getClass().isArray()) {
            return obj.toString();
        }
        if (obj instanceof boolean[]) {
            return Arrays.toString((boolean[]) obj);
        }
        if (obj instanceof byte[]) {
            return Arrays.toString((byte[]) obj);
        }
        if (obj instanceof char[]) {
            return Arrays.toString((char[]) obj);
        }
        if (obj instanceof short[]) {
            return Arrays.toString((short[]) obj);
        }
        if (obj instanceof int[]) {
            return Arrays.toString((int[]) obj);
        }
        if (obj instanceof long[]) {
            return Arrays.toString((long[]) obj);
        }
        if (obj instanceof float[]) {
            return Arrays.toString((float[]) obj);
        }
        if (obj instanceof double[]) {
            return Arrays.toString((double[]) obj);
        }
        if (obj instanceof Object[]) {
            return Arrays.deepToString((Object[]) obj);
        }
        return "Couldn't find a correct type for the object";
    }

}
