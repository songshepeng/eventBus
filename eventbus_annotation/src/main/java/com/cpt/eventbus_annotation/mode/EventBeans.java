package com.cpt.eventbus_annotation.mode;

public class EventBeans implements SubscriberInfo {
    //订阅者对象CLass， 如: MainActivity. class
    private final Class subscriberClass;
    //订阅方法数组，参SimpleSubscriberInfo. java 25行
    private final SubscriberMethod[] methodInfo;

    public EventBeans(Class subscriberClass, SubscriberMethod[] methodInfos) {
        this.subscriberClass = subscriberClass;
        this.methodInfo = methodInfos;
    }

    @Override
    public Class<?> getSubscriberClass() {
        return subscriberClass;
    }

    @Override
    public SubscriberMethod[] getSubscriberMethods() {
        return methodInfo;
    }
}
