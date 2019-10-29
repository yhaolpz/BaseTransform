package com.wyh.plugin;

import java.util.Objects;

import javassist.CtMethod;

/**
 * @author WangYingHao
 * @since 2019-10-28
 */
public class CtClassUtil {


    /**
     * 方法上是否有某注解
     */
    public static boolean hasAnnotation(CtMethod ctMethod, String annotation) throws ClassNotFoundException {
        Object[] annotations = ctMethod.getAnnotations();
        if (annotations != null) {
            for (Object annotation1 : annotations) {
                if (Objects.equals(annotation, annotation1.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

}
