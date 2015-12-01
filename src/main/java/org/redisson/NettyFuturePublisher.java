package org.redisson;

import org.reactivestreams.Subscriber;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.support.Exceptions;
import reactor.rx.Stream;
import reactor.rx.action.Action;
import reactor.rx.subscription.ReactiveSubscription;

public class NettyFuturePublisher<T> extends Stream<T> {

    private final Future<? extends T> that;

    public NettyFuturePublisher(Future<? extends T> that) {
        this.that = that;
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new ReactiveSubscription<T>(this, subscriber) {

                @Override
                public void request(long elements) {
                    Action.checkRequest(elements);
                    if (isComplete()) return;

                    that.addListener(new FutureListener<T>() {
                        @Override
                        public void operationComplete(Future<T> future) throws Exception {
                            if (!future.isSuccess()) {
                                subscriber.onError(future.cause());
                                return;
                            }

                            if (future.getNow() != null) {
                                subscriber.onNext(future.getNow());
                            }
                            onComplete();
                        }
                    });
                }
            });
        } catch (Throwable throwable) {
            Exceptions.throwIfFatal(throwable);
            subscriber.onError(throwable);
        }
    }


}
