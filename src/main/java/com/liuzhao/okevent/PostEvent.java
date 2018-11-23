package com.liuzhao.okevent;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author liuzhao
 */
public final class PostEvent {

    private static final List<PostEvent> EVENT_POOL = new ArrayList<>();

    Object event;
    PostEvent next;
    Subscription subscription;

    public PostEvent(Object event, Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    public static PostEvent obtainFromSubscription(Subscription subscription, Object event){
        synchronized (EVENT_POOL){
            int size = EVENT_POOL.size();
            if (EVENT_POOL.size() > 0){
                PostEvent postEvent = EVENT_POOL.remove(size - 1);
                postEvent.event = event;
                postEvent.subscription = subscription;
                postEvent.next = null;
                return postEvent;
            }
        }
        return new PostEvent(event, subscription);
    }

    public static void releasePostEvent(PostEvent event){
        event.event = null;
        event.subscription = null;
        event.next = null;
        synchronized (EVENT_POOL){
            if (EVENT_POOL.size() < 1000){
                EVENT_POOL.add(event);
            }
        }

    }


}
