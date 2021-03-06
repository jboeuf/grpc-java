/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Issues blank {@link ListenableFuture}s when requested, and later fulfills them, either by linking
 * them with upstream {@code ListenableFuture}s that will eventually be completed, or by failing
 * them immediately.
 *
 * <p>This is useful for {@code ListenableFuture} providers that may be requested at a moment when
 * the necessary information for providing a {@code ListenableFuture} is not available, but will be
 * later.
 */
@NotThreadSafe
public final class BlankFutureProvider<T> {
  private List<SettableFuture<T>> blankFutures = new ArrayList<SettableFuture<T>>();

  /**
   * Creates a blank future and track it.
   */
  public ListenableFuture<T> newBlankFuture() {
    SettableFuture<T> future = SettableFuture.create();
    blankFutures.add(future);
    return future;
  }

  /**
   * Creates a {@link FulfillmentBatch} that will be used to fulfill the currently tracked blank
   * futures.
   *
   * <p>After this method has returned, the {@link BlankFutureProvider} will no longer track the
   * previous blank futures, and can be used to create and track new blank futures.
   */
  public FulfillmentBatch<T> createFulfillmentBatch() {
    List<SettableFuture<T>> blankFuturesCopy = blankFutures;
    blankFutures = new ArrayList<SettableFuture<T>>();
    return new FulfillmentBatch<T>(blankFuturesCopy);
  }

  /**
   * A batch of blank futures that are going to be fulfilled, by either linking them with other
   * futures, or failing them.
   *
   * <p>This object is independent from the {@link BlankFutureProvider} that created it. They don't
   * need synchronization between them.
   */
  public static class FulfillmentBatch<T> {
    private final List<SettableFuture<T>> futures;

    private FulfillmentBatch(List<SettableFuture<T>> futures) {
      this.futures = Preconditions.checkNotNull(futures, "futures");
    }

    /**
     * Links the blank futures with futures that will be eventually completed.
     *
     * <p>For each blank future, this method calls {@link Supplier#get()} on {@code source} and link
     * the returned future to the blank future.
     */
    public void link(Supplier<ListenableFuture<T>> source) {
      for (final SettableFuture<T> future : futures) {
        ListenableFuture<T> sourceFuture = source.get();
        Futures.addCallback(sourceFuture, new FutureCallback<T>() {
          @Override public void onSuccess(T result) {
            future.set(result);
          }

          @Override public void onFailure(Throwable t) {
            future.setException(t);
          }
        });
      }
    }

    /**
     * Fails all futures with the given error.
     */
    public void fail(Throwable error) {
      for (SettableFuture<T> future : futures) {
        future.setException(error);
      }
    }
  }
}
