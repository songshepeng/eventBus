package com.cpt.eventbus;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cpt.eventbus_annotation.annotation.SubscriberInfoIndex;
import com.cpt.eventbus_annotation.mode.EventBeans;
import com.cpt.eventbus_annotation.mode.SubscriberInfo;
import com.cpt.eventbus_annotation.mode.SubscriberMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventBus {

    // volatile 修饰的变量不允许线程内部级存和重排序即直接修改内存
    private static volatile EventBus defaultInstance;
    //索引接口
    private SubscriberInfoIndex subscriberInfoIndexes;
    //订阅者类型集合，比如:订阅者MainActivity订阅I °哪些EventBean. 或者解除订阅的缓存。
// key: 订阅者MainActivity.class, value: EventBean失合
    private Map<Object, List<Class<?>>> typesBySubscriber;
    //方法缓存: key: 订阅者MainActivity.class， value: 订阅方法集合
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    // EventBean缓存，key: UserInfo.class， value: 订阅者(可以是多MActivity)中所有订阅的方法集合
    private Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    //粘性事件缓存。key: UserInfo. class， value: UserInfo
    private final Map<Class<?>, Object> stickyEvents;
    //发送(子线程)-订阅(主线程)
    private Handler handler;
    //发送(主线程).订阅(于线程)
    private ExecutorService executorService;
    private String TAG = EventBeans.class.getName();

    private EventBus() {
        //初始化缓存集合
        typesBySubscriber = new HashMap<>();
        subscriptionsByEventType = new HashMap<>();
        stickyEvents = new HashMap<>();
        // Handler高级用法:将handler 放在主线程使用
        handler = new Handler(Looper.getMainLooper());
        //创建一个子线程(缓存线程池)
        executorService = Executors.newCachedThreadPool();
    }

    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    //添加索引(简化)，接口=接口实现类，参EventBusBuilder. java 136行
    public void addIndex(SubscriberInfoIndex index) {
        subscriberInfoIndexes = index;
    }

    //注册/订阅事件，参考EventBus. java 138行
    public void register(Object subscriber) {
        //获取MainActivity. class
        Class<?> subscriberClass = subscriber.getClass();
        // 寻找(MainActivity.class) 订阅方法集合
        Log.e(TAG, "register: 开始注册" );
        List<SubscriberMethod> subscriberMethods = findSubscriberMethods(subscriberClass);
        Log.e(TAG, "register:  获取事件方法"+subscriberMethods.size() );
        for (SubscriberMethod subscriberMethod : subscriberMethods) {
            Log.e(TAG, "register: "+subscriberMethod.toString() );
        }
        synchronized (this) { //同步锁，并发少可考虑删除(参考源码)
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                //遍历后，开始订阅
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

    //寻找(MainActivity.class)订阅方法集合，参SubscriberMethodFinder. java 55行
    private List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //从方法缓存中读取
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        //找到缓存直接获取
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        Log.e(TAG, "findSubscriberMethods:  缓存为空 从APT文件拿数据" );
        //找不到从生成的APT文件中寻找
        subscriberMethods = findUsingInfo(subscriberClass);
        if (subscriberMethods != null) {
            //载入缓存
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
        }
        return subscriberMethods;
    }


    //从APT 生成的类文件中寻找订阅方法集合，书SubscriberMethodFinder. java 64行
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // app在运行时寻找索引，报错了则说明没有初始化索引方法
        if (subscriberInfoIndexes == null) {
            throw new RuntimeException(" 未添加索引方法: addIndex()");
        }

        //接口持有实现类的引用
        SubscriberInfo info = subscriberInfoIndexes.getSubscriberInfo(subscriberClass);
        Class<?> subscriberClass1 = info.getSubscriberClass();
        Log.e(TAG, "findUsingInfo:  name :"+subscriberClass1.getName() );
        SubscriberMethod[] subscriberMethods = info.getSubscriberMethods();
        if (subscriberMethods.length<1){

            Log.e(TAG, "findUsingInfo:  数组为空" );
        }
        //数组转ist集合，参EventBus生成的APT类文件
        if (info != null){
            Log.e(TAG, "findUsingInfo:  获取数组"+info.getSubscriberMethods().length );
            return Arrays.asList(info.getSubscriberMethods());
        }
        return null;
    }


    //是否已经注册/订阅，参考EventBus.java 217行
    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    //解除某订阅者关系，参EventBus.java 239行
    public synchronized void unregister(Object subscriber) {
        //从缓存中移除
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            //移除前清空集合
            subscribedTypes.clear();
            typesBySubscriber.remove(subscriber);
        }
    }

    //发送粘性事件，最终还是调用了post方法。参EventBus.java 301 行
    public void postSticky(Object event) {
        //同步锁保证并发安全(小项目可忽略此处)
        synchronized (stickyEvents) {
            //加入粘性事件级存集合
            stickyEvents.put(event.getClass(), event);
            //巨坑! ! !源码这么写我也不知道什么意图。悲心的后果:只要参数四配，粘性/非粘性订阅方法全部执行I
            // post(event);
        }
    }

    //获取指定类型的粘性事件，参考EventBus. java 314行
    public <T> T getStickyEvent(Class<T> eventType) {
        //同步锁保证并发安全(小项目可忽略此处)
        synchronized (stickyEvents) {
            // cast 方法做转换类型时安全措施(简化stickyEvents. get(eventType) )
            return eventType.cast(stickyEvents.get(eventType));
        }
    }


    //移除指定类型的粘性事件(此处返回值看自己需求，可为oolean) ，参EventBus . java 325行
    public <T> T removeStickyEvent(Class<T> eventType) {
        //同步锁保证并发安全(小项目可忽略此处)
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    //移除所有粘性事件，参EventBus. java 352行
    public void removeAllStickyEvents() {
        // 同步锁保证并发安全(小项目可忽略此处)
        synchronized (stickyEvents) {
            //清理集合
            stickyEvents.clear();
        }
    }

    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        //获取订阅的方法参数类型，如userInfo.class
        Class<?> eventType = subscriberMethod.getEventType();
        //临时对象存储
        Subscription subscription = new Subscription(subscriber, subscriberMethod);
        //获取eventBean缓存对象
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(subscription)) {
                Log.e(TAG, subscriber.getClass() + "重复注册粘性事件");
                //执行多次粘性事件。但不添加到集合，避免订阅方法多次执行
                sticky(subscriberMethod, eventType, subscription);
                return;
            }else {
                subscriptionsByEventType.put(eventType,subscriptions);
            }
        }

        //订阅方法优先级处理，第一次进来肯定是0 参考EventBus.java 163行
        int size = subscriptions.size();
        //这里的i<= size否则进入不了下面的条件
        for (int i = 0; i <= size; i++) {
            //如果满足任一条件则进入循环(第1次i = size = 0)
            //第2次size不为，新加入的订圆方法匹配集合中所有订圆方法的优先级
            if (i == size || subscriberMethod.getPriority() > subscriptions.get(i).subscriberMethod.getPriority()) {
                //如果新加入的订阅方法优先级大于集合中某订阅方法优先级，则插队到它之前- -位
                if (!subscriptions.contains(subscription)) subscriptions.add(i, subscription);
                //优化:插队成功就跳出(找到了加入集合点)
                break;
            }
        }

        //订阅者类型集合， 比如:订阅MainActivity汀阅了哪些EventBean,或者解除订阅的缓存
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            //存入缓存
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        //注意: subscribe() 方法在遍历过程中，所以一直在添加
        subscribedEvents.add(eventType);
        sticky(subscriberMethod, eventType, subscription);
    }


    //发送消息/事件
    public void post(Object event) {
//此处两个参数，简化了源码，参EventBus.java 252, - 265 - 384 - 40行
        postSingleEventForEventType(event,event.getClass());
    }

    //为EventBean 事件类型发布单个事件(遍历)，EventBus核心:参数类型必须- 致! ! !
    private void postSingleEventForEventType(Object event, Class<?> eventClass) {
        //从EventBean缓存中，获取所有订圆者和订阅方法
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            //同步锁，保证并发安全
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        //判空。健壮性代码
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                //遍历，寻找发送方指定的EventBean，匹配的订阅方法的EventBean
                postToSubscription(subscription, event);
            }
        }else {
            Log.e(TAG, "postSingleEventForEventType:  获取数据为空" );
        }
    }

    //抽取原因:可执行多次粘性事件，而不会出现闪退，参考EventBus . java 158行
    private void sticky(SubscriberMethod subscriberMethod, Class<?> eventType, Subscription subscription) {
        //粘性事件触发:注册事件就激活方法，因为整个源码只有此处遍历了。
        //最佳切入点原因: 1，粘性事件的订阅方法加入了缓存。2.注册时只有粘性事件直按激活方法(脂离非粘性事件)
        //新增开关方法弊端:粘性事件未在缓存中， 无法触发订阅方法。且有可能多次执行post()方法
        if (subscriberMethod.isSticky()) {
            //源码中做了继承关系的处理，也说明了迭代效率和更改数据结构方便查找，这里就省略了(真实项目极少)
            Object stickyEvent = stickyEvents.get(eventType);
            //发送事件到订阅者的所有订阅方法，并激活方法
            if (stickyEvent != null) postToSubscription(subscription, stickyEvent);
        }
    }

    //发送事件到订阅者的所有订阅方法(......。参考参考EventBus.java 427行
    private void postToSubscription(final Subscription subscription, final Object event) {
        switch (subscription.subscriberMethod.getThreadMode()) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                //订阅方是主线程，则主一去
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    invokeSubscriber(subscription, event);
                } else {
                    //订阅方是于线程，则子一主
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription, event);
                        }
                    });
                }
                break;
            case ASYNC:
                //订阅方是主线程，则主-子
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    //主线程-子线程， 创建一一个 子线程(缓存线程池)
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription, event);
                        }
                    });
                } else {
                    //订阅方是子线程，则子-子
                    invokeSubscriber(subscription, event);
                }
                break;
            default:
                throw new IllegalStateException("未知线程模式! " + subscription.subscriberMethod.getThreadMode());
        }
    }

    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            //无论3.0之前还是之后。最后一 步终究逃不过反射!
            subscription.subscriberMethod.getMethod().invoke(subscription.subscriber, event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //清理静态缓存(视项目规模调用)
    public static void clearCaches() {
        METHOD_CACHE.clear();
    }
}
