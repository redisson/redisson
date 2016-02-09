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
package org.redisson.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.CompleteFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Invokes Future listeners in the same thread unlike {@code SucceededFuture} does.
 *
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public abstract class FastCompleteFuture<V> extends CompleteFuture<V> {

  private static final Logger logger = LoggerFactory.getLogger(FastCompleteFuture.class);

  protected FastCompleteFuture() {
    super(null);
  }

  @Override
  public Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
    if (listener == null) {
      throw new NullPointerException("listener");
    }

    notify(listener);
    return this;
  }

  private void notify(GenericFutureListener<? extends Future<? super V>> listener) {
    try {
      ((GenericFutureListener) listener).operationComplete(this);
    } catch (Throwable t) {
      if (logger.isWarnEnabled()) {
        logger.warn("An exception was thrown by " + listener.getClass().getName()
            + ".operationComplete()", t);
      }
    }
  }

  @Override
  public Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
    if (listeners == null) {
      throw new NullPointerException("listeners");
    }
    for (GenericFutureListener<? extends Future<? super V>> l : listeners) {
      if (l == null) {
        break;
      }
      notify(l);
    }
    return this;
  }


}
