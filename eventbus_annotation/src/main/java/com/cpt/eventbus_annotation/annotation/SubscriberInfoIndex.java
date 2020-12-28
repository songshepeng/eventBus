package com.cpt.eventbus_annotation.annotation;

import com.cpt.eventbus_annotation.mode.SubscriberInfo;

/*
  所有的事件订阅方法
 */
public interface SubscriberInfoIndex {
    SubscriberInfo getSubscriberInfo(Class<?>  SubscriberClass );
}
