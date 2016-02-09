/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.RCollectionReactive;

import reactor.rx.Promise;
import reactor.rx.Promises;
import reactor.rx.action.support.DefaultSubscriber;

public class PublisherAdder<V> {

  private final RCollectionReactive<V> destination;

  public PublisherAdder(RCollectionReactive<V> destination) {
    this.destination = destination;
  }

  public Long sum(Long first, Long second) {
    return first + second;
  }

  public Publisher<Long> addAll(Publisher<? extends V> c) {
    final Promise<Long> promise = Promises.prepare();

    c.subscribe(new DefaultSubscriber<V>() {

      Subscription s;
      Long lastSize = 0L;
      V lastValue;

      @Override
      public void onSubscribe(Subscription s) {
        this.s = s;
        s.request(1);
      }

      @Override
      public void onNext(V o) {
        lastValue = o;
        destination.add(o).subscribe(new DefaultSubscriber<Long>() {

          @Override
          public void onSubscribe(Subscription s) {
            s.request(1);
          }

          @Override
          public void onError(Throwable t) {
            promise.onError(t);
          }

          @Override
          public void onNext(Long o) {
            lastSize = sum(lastSize, o);
          }

          @Override
          public void onComplete() {
            lastValue = null;
            s.request(1);
          }
        });
      }

      @Override
      public void onComplete() {
        if (lastValue == null) {
          promise.onNext(lastSize);
        }
      }
    });

    return promise;
  }

}
