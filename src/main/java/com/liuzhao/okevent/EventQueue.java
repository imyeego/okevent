package com.liuzhao.okevent;

/**
 *
 * @author liuzhao
 */
public class EventQueue {

    private PostEvent head;
    private PostEvent tail;

    public synchronized void enqueue(PostEvent event){
        if (event == null)
            throw new NullPointerException("");

        if (tail != null) {
            tail.next = event;
            tail = event;
        } else if (head == null) {
            head = tail = event;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        notifyAll();
    }

    public synchronized PostEvent poll(){
        PostEvent event = head;
        if (head != null) {
            head = head.next;
            if (head == null)
                tail = null;
        }
        return event;
    }

    public synchronized PostEvent poll(final int maxMillisToWait) throws InterruptedException{
        if (head == null){
            wait(maxMillisToWait);
        }

        return poll();
    }
}
