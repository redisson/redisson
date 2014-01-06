/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.redisson.misc.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A thread gate, that uses an {@link java.util.concurrent.locks.AbstractQueuedSynchronizer}.
 * <p/>
 * This implementation allows you to create a latch with a default state (open or closed), and repeatedly open or close
 * the latch.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @since 4.0
 */
public class ReclosableLatch extends AbstractQueuedSynchronizer {

   private static final long serialVersionUID = 1744280161777661090l;

   // the following states are used in the AQS.
   private static final int OPEN_STATE = 0, CLOSED_STATE = 1;

   public ReclosableLatch() {
      setState(CLOSED_STATE);
   }

   public ReclosableLatch(boolean defaultOpen) {
      setState(defaultOpen ? OPEN_STATE : CLOSED_STATE);
   }

   @Override
   public final int tryAcquireShared(int ignored) {
      // return 1 if we allow the requestor to proceed, -1 if we want the requestor to block.
      return getState() == OPEN_STATE ? 1 : -1;
   }

   @Override
   public final boolean tryReleaseShared(int state) {
      // used as a mechanism to set the state of the Sync.
      setState(state);
      return true;
   }

   public final void open() {
      // do not use setState() directly since this won't notify parked threads.
      releaseShared(OPEN_STATE);
   }

   public final void close() {
      // do not use setState() directly since this won't notify parked threads.
      releaseShared(CLOSED_STATE);
   }

   public boolean isOpened() {
      return getState() == OPEN_STATE;
   }

   public final void await() throws InterruptedException {
      acquireSharedInterruptibly(1); // the 1 is a dummy value that is not used.
   }

   public final boolean await(long time, TimeUnit unit) throws InterruptedException {
      return tryAcquireSharedNanos(1, unit.toNanos(time)); // the 1 is a dummy value that is not used.
   }

   @Override
   public String toString() {
      int s = getState();
      String q = hasQueuedThreads() ? "non" : "";
      return "ReclosableLatch [State = " + s + ", " + q + "empty queue]";
   }
}
