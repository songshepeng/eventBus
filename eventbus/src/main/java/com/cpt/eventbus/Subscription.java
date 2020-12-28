package com.cpt.eventbus;

import androidx.annotation.Nullable;

import com.cpt.eventbus_annotation.mode.SubscriberMethod;

/**
 * 临时的javaBean 变量
 */
public class Subscription {
    final  Object subscriber ;

    final SubscriberMethod subscriberMethod ;

    public Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        //必须重写方法 检测激活粘性事件的重复性 （同一对象注册多个）
        if (obj instanceof Subscription) {
            Subscription otherSubscription  = (Subscription) obj;
            //删除官方: subscriber == othersubscription. subscriber判断条件
           //原因:粘性事件Bug，多次调用和移除时重现，参Subscription. java 37行
            return subscriberMethod.equals(otherSubscription.subscriberMethod);
        }else {
            return false ;
        }
    }
}
