package com.wyh.mylibrary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author WangYingHao
 * @since 2019-10-25
 */
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface Aop {
}
