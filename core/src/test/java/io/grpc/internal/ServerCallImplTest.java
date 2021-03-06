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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.CharStreams;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

@RunWith(JUnit4.class)
public class ServerCallImplTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Mock private ServerStream stream;

  private ServerCallImpl<Long, Long> call;

  private final MethodDescriptor<Long, Long> method = MethodDescriptor.create(
      MethodType.UNARY, "/service/method", new LongMarshaller(), new LongMarshaller());

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    call = new ServerCallImpl<Long, Long>(stream, method);
  }

  @Test
  public void request() {
    call.request(10);

    verify(stream).request(10);
  }

  @Test
  public void sendHeader_firstCall() {
    Metadata headers = new Metadata();

    call.sendHeaders(headers);

    verify(stream).writeHeaders(headers);
  }

  @Test
  public void sendHeader_failsOnSecondCall() {
    call.sendHeaders(new Metadata());
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("sendHeaders has already been called");

    call.sendHeaders(new Metadata());
  }

  @Test
  public void sendHeader_failsOnClosed() {
    call.close(Status.CANCELLED, new Metadata());

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("call is closed");

    call.sendHeaders(new Metadata());
  }

  @Test
  public void sendMessage() {
    call.sendHeaders(new Metadata());
    call.sendMessage(1234L);

    verify(stream).writeMessage(isA(InputStream.class));
    verify(stream).flush();
  }

  @Test
  public void sendMessage_failsOnClosed() {
    call.sendHeaders(new Metadata());
    call.close(Status.CANCELLED, new Metadata());

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("call is closed");

    call.sendMessage(1234L);
  }

  @Test
  public void sendMessage_failsIfheadersUnsent() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("sendHeaders has not been called");

    call.sendMessage(1234L);
  }

  @Test
  public void sendMessage_closesOnFailure() {
    call.sendHeaders(new Metadata());
    doThrow(new RuntimeException("bad")).when(stream).writeMessage(isA(InputStream.class));

    try {
      call.sendMessage(1234L);
      fail();
    } catch (RuntimeException e) {
      // expected
    }

    verify(stream).close(isA(Status.class), isA(Metadata.class));
  }

  @Test
  public void isReady() {
    when(stream.isReady()).thenReturn(true);

    assertTrue(call.isReady());
  }


  private static class LongMarshaller implements Marshaller<Long> {
    @Override
    public InputStream stream(Long value) {
      return new ByteArrayInputStream(value.toString().getBytes());
    }

    @Override
    public Long parse(InputStream stream) {
      try {
        return Long.parseLong(CharStreams.toString(new InputStreamReader(stream)));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
