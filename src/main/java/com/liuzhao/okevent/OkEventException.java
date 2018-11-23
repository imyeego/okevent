package com.liuzhao.okevent;


/**
 * An {@link RuntimeException } thrown in cases something went wrong inside OkEvent.
 *
 * @author liuzhao
 */
public class OkEventException extends RuntimeException{


    private static final long serialVersionUID = -4274023664413924934L;

    public OkEventException(String detailMessage) {
        super(detailMessage);
    }

    public OkEventException(Throwable throwable) {
        super(throwable);
    }

    public OkEventException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
