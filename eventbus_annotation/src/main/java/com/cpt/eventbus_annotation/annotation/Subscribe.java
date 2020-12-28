package com.cpt.eventbus_annotation.annotation;

import com.cpt.eventbus_annotation.mode.ThreadMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Subscribe {
    //线程模式，默认推孝POSTING (订阅、发布在同-线程)
    ThreadMode threadMode() default ThreadMode.POSTING ;
    //是否使用粘性事件
    boolean sticky() default  false;
    //事件订阅优先级，在同一个线程中。 数值越大优先级越高。
    int priority() default 0 ;
}
