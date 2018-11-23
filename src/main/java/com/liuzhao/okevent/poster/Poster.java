package com.liuzhao.okevent.poster;

import com.liuzhao.okevent.Subscription;

/**
 *
 * @author liuzhao
 */
public interface Poster {

    void enqueue(Subscription subscription, Object event);
}
