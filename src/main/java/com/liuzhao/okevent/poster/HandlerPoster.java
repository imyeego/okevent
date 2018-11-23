package com.liuzhao.okevent.poster;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.liuzhao.okevent.EventQueue;
import com.liuzhao.okevent.OkEvent;
import com.liuzhao.okevent.OkEventException;
import com.liuzhao.okevent.PostEvent;
import com.liuzhao.okevent.Subscription;

import java.util.HashMap;

/**
 *
 * @author liuzhao
 */
public class HandlerPoster extends Handler implements Poster {

    private final EventQueue eventQueue;
    private final OkEvent okEvent;
    private final int maxHandlerTimes;

    public HandlerPoster(OkEvent okEvent, Looper looper, int maxHandlerTimes){
        super(looper);
        this.okEvent = okEvent;
        this.maxHandlerTimes = maxHandlerTimes;
        eventQueue = new EventQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PostEvent postEvent = PostEvent.obtainFromSubscription(subscription, event);
        synchronized (this){
            eventQueue.enqueue(postEvent);
            if (!sendMessage(obtainMessage())){
                throw new OkEventException("Could not send handler message");
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        long started = SystemClock.uptimeMillis();
        while (true){
            PostEvent postEvent = eventQueue.poll();
            if (postEvent == null){
                synchronized (this){
                    postEvent = eventQueue.poll();
                    if (postEvent == null) return;
                }
            }

            okEvent.invokeSubscribe(postEvent);
            long timeInMethod = SystemClock.uptimeMillis() - started;
            if (timeInMethod >= maxHandlerTimes){
                if (!sendMessage(obtainMessage()))
                    throw new OkEventException("Could not send handler message");
                return;
            }
        }
    }
}
