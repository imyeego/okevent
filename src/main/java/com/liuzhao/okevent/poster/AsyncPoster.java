package com.liuzhao.okevent.poster;

import com.liuzhao.okevent.EventQueue;
import com.liuzhao.okevent.OkEvent;
import com.liuzhao.okevent.PostEvent;
import com.liuzhao.okevent.Subscription;

/**
 *
 * @author liuzhao
 */
public class AsyncPoster implements Runnable, Poster {

    private final OkEvent okEvent;
    private final EventQueue eventQueue;

    public AsyncPoster(OkEvent okEvent) {
        this.okEvent = okEvent;
        eventQueue = new EventQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PostEvent postEvent = PostEvent.obtainFromSubscription(subscription, event);
        eventQueue.enqueue(postEvent);
        okEvent.getExecutorService().execute(this);
    }

    @Override
    public void run() {
        PostEvent postEvent = eventQueue.poll();
        if (postEvent == null)
            throw new IllegalStateException("No post event available");

        okEvent.invokeSubscribe(postEvent);
    }
}
