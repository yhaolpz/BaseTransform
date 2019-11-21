package com.test;

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
public @interface Aop2 {
    //todo 注解中如果有枚举属性，就会在调用获取方法上的枚举方法时报 classNotFoundException 错误
//    Mode mode() default Mode.LOW;
}
