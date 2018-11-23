package com.liuzhao.okevent;

import android.os.Looper;

import com.liuzhao.okevent.poster.AsyncPoster;
import com.liuzhao.okevent.poster.BackgroundPoster;
import com.liuzhao.okevent.poster.Poster;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author liuzhao
 */
public final class OkEvent {

    private static final String TAG = "OkEvent";
    static volatile OkEvent okEvent;
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;

    private final MainThreadSupport mainThreadSupport;
    private final Poster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;


    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };

    private final ExecutorService executorService;
    private final boolean eventInheritance;


    public static OkEvent getInstance(){
        if (okEvent == null){
            synchronized (OkEvent.class){
                if (okEvent == null){
                    okEvent = new OkEvent();
                }
            }
        }
        return okEvent;
    }

    private OkEvent(){
        executorService = DEFAULT_EXECUTOR_SERVICE;
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        mainThreadSupport = createMainThreadSupport();
        mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
        eventInheritance = true;
    }

    private MainThreadSupport createMainThreadSupport() {

        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else {
            Object looperOrNull = getAndroidMainLooperOrNull();
            return looperOrNull == null ? null :
                    new MainThreadSupport.AndroidHandlerMainThreadSupport((Looper) looperOrNull);
        }
    }

    private Object getAndroidMainLooperOrNull() {
        try {
            return Looper.getMainLooper();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isMainThread(){
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    public void invokeSubscribe(PostEvent postEvent){
        Object event = postEvent.event;
        Subscription subscription = postEvent.subscription;
        PostEvent.releasePostEvent(postEvent);
        invokeSubscriber(subscription, event);
    }

    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    public void register(Object subscriber){
        Class<?> subscriberClass = subscriber.getClass();
        List<SubscriberMethod> subscriberMethods = findSubscriberMethods(subscriberClass);
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod){
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new OkEventException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }
        int size = subscriptions.size();
        subscriptions.add(size, newSubscription);

        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        if (subscriberMethod.sticky){
            if (eventInheritance){
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            }else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    private List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass){
        List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        Method[] methods;

        try {
            methods = subscriberClass.getDeclaredMethods();
        } catch (SecurityException e) {
            methods = subscriberClass.getMethods();
        }
        for (Method method : methods){
            int modifiers = method.getModifiers();
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0){
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1){
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null){
                        Class<?> eventType = parameterTypes[0];
                        ThreadMode threadMode = subscribeAnnotation.threadMode();
                        subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                subscribeAnnotation.sticky()));
                    }
                }else if (method.isAnnotationPresent(Subscribe.class)){
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new OkEventException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            }else if (method.isAnnotationPresent(Subscribe.class)){
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new OkEventException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }

        }

        return subscriberMethods;
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber. */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    public void unregister(Object subscriber){
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void post(Object event){
        PostingThreadState postingState = currentPostingThreadState.get();
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting){
            postingState.isMainThread = isMainThread();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new OkEventException("Internal error. Abort state was not reset");
            }

            try {
                while (!eventQueue.isEmpty()){
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error{
        Class<?> eventClass = event.getClass();
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
        }
    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode){
            case POSTING:
                invokeSubscriber(subscription, event);
                break;

            case MAIN:
                if (isMainThread){
                    invokeSubscriber(subscription, event);
                }else{
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;

            case BACKGROUND:
                if (isMainThread){
                    backgroundPoster.enqueue(subscription, event);
                }else {
                    invokeSubscriber(subscription, event);
                }
                break;

            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;

            default:
                throw new IllegalStateException("Unknown thread mode: \" + subscription.subscriberMethod.threadMode");
        }

    }

    public void postSticky(Object event){
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);

    }

    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    /** For ThreadLocal, much faster to set (and get multiple values). */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }
}
