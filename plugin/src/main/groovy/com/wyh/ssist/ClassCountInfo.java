package com.wyh.ssist;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author WangYingHao
 * @since 2019/11/25
 */
public class ClassCountInfo {
    /**
     * 未发生变更文件数量
     * 对源码文件夹来说是指类文件，对jar文件来说是指jar文件
     */
    static final String FILE_NOTCHANGED = "NOTCHANGED";
    /**
     * 新增文件数量
     * 对源码文件夹来说是指类文件，对jar文件来说是指jar文件
     */
    static final String FILE_ADDED = "ADDED";
    /**
     * 更新文件数量
     * 对源码文件夹来说是指类文件，对jar文件来说是指jar文件
     */
    static final String FILE_CHANGED = "CHANGED";
    /**
     * 移除文件数量
     * 对源码文件夹来说是指类文件，对jar文件来说是指jar文件
     */
    static final String FILE_REMOVED = "REMOVED";
    /**
     * 文件总数量，等于以上四种变更状态类型文件总和
     * 对源码文件夹来说是指类文件，对jar文件来说是指jar文件
     */
    static final String FILE_ALL = "ALL";
    /**
     * 类文件尝试转换数量
     */
    static final String CLASS_TRANSFORM_TRY = "TRANSFORM_TRY";
    /**
     * 类文件转换数量
     */
    static final String CLASS_TRANSFORM = "TRANSFORM";


    private ConcurrentHashMap<String, Integer> mFileCountMap = new ConcurrentHashMap<>();

    void add(String key) {
        if (mFileCountMap.containsKey(key)) {
            Integer count = mFileCountMap.get(key);
            mFileCountMap.put(key, ++count);
        } else {
            mFileCountMap.put(key, 1);
        }
    }

    void clean() {
        mFileCountMap.clear();
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String key : mFileCountMap.keySet()) {
            stringBuilder.append(key);
            stringBuilder.append(":");
            stringBuilder.append(mFileCountMap.get(key).toString());
            stringBuilder.append(", ");
        }
        return stringBuilder.toString();
    }

}
