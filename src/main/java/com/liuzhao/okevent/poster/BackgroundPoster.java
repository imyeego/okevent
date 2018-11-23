package com.liuzhao.okevent.poster;

import com.liuzhao.okevent.EventQueue;
import com.liuzhao.okevent.OkEvent;
import com.liuzhao.okevent.PostEvent;
import com.liuzhao.okevent.Subscription;

/**
 *
 * @author liuzhao
 */
public final class BackgroundPoster implements Runnable, Poster{

    private final OkEvent okEvent;
    private final EventQueue eventQueue;
    private volatile boolean isRunning;

    public BackgroundPoster(OkEvent okEvent) {
        this.okEvent = okEvent;
        this.eventQueue = new EventQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PostEvent postEvent = PostEvent.obtainFromSubscription(subscription, event);
        synchronized (this){
            eventQueue.enqueue(postEvent);
            if (!isRunning){
                isRunning = true;
                okEvent.getExecutorService().execute(this);
            }
        }
    }

    @Override
    public void run() {
        try {
            try {
                while (true) {
                    PostEvent postEvent = eventQueue.poll(1000);
                    if (postEvent == null) {
                        synchronized (this) {
                            // Check again, this time in synchronized
                            postEvent = eventQueue.poll();
                            if (postEvent == null) {
                                isRunning = false;
                                return;
                            }
                        }
                    }
                    okEvent.invokeSubscribe(postEvent);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            isRunning = false;
        }
    }
}
