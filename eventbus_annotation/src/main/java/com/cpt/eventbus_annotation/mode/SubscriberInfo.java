package com.cpt.eventbus_annotation.mode;

public interface SubscriberInfo {
    //订阅者所属的类
    Class<?> getSubscriberClass();
    //获取订阅所属类中所有订阅事件的方法(此处不使List是因为注解处理器每次都数ist.clear(),麻烦! )

    SubscriberMethod[] getSubscriberMethods();
}
