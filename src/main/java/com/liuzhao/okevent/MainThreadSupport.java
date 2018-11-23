package com.liuzhao.okevent;

import android.os.Looper;

import com.liuzhao.okevent.poster.HandlerPoster;
import com.liuzhao.okevent.poster.Poster;

/**
 *
 * @author liuzhao
 */
interface MainThreadSupport {
    boolean isMainThread();

    Poster createPoster(OkEvent okEvent);

    class AndroidHandlerMainThreadSupport implements MainThreadSupport {

        private final Looper looper;

        public AndroidHandlerMainThreadSupport(Looper looper) {
            this.looper = looper;
        }

        @Override
        public boolean isMainThread() {
            return looper == Looper.myLooper();
        }

        @Override
        public Poster createPoster(OkEvent okEvent) {
            return new HandlerPoster(okEvent, looper, 10);
        }
    }
}
