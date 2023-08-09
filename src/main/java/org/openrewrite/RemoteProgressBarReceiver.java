/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import org.openrewrite.RemoteProgressBarSender.Request.Type;
import org.openrewrite.internal.lang.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.openrewrite.RemoteProgressBarSender.MAX_MESSAGE_SIZE;
import static java.util.Objects.requireNonNull;

public class RemoteProgressBarReceiver implements ProgressBar {
    private final ProgressBar delegate;

    private final DatagramSocket socket;

    public RemoteProgressBarReceiver(ProgressBar delegate) {
        this.delegate = delegate;
        try {
            this.socket = new DatagramSocket(RemoteProgressBarSender.PORT);
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void run() {
        try {
            byte[] buf = new byte[MAX_MESSAGE_SIZE]; // no message should be longer than a terminal line length
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            Type type = null;
            for (Type t : Type.values()) {
                if (t.ordinal() == buf[0]) {
                    type = t;
                    break;
                }
            }

            if (type == null) {
                return;
            }

            String message = null;
            if (packet.getLength() > 1) {
                message = new String(Arrays.copyOfRange(buf, 1, packet.getLength()),
                        StandardCharsets.UTF_8);
            }

            switch (type) {
                case IntermediateResult:
                    delegate.intermediateResult(message);
                    break;
                case Finish:
                    delegate.finish(message);
                    break;
                case Close:
                    delegate.close();
                    break;
                case Step:
                    delegate.step();
                    break;
                case SetExtraMessage:
                    delegate.setExtraMessage(message);
                    break;
                case SetMax:
                    delegate.setMax(Integer.parseInt(requireNonNull(message)));
                    break;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void intermediateResult(@Nullable String message) {
        delegate.intermediateResult(message);
    }

    @Override
    public void finish(String message) {
        delegate.finish(message);
    }

    @Override
    public void step() {
        delegate.step();
    }

    @Override
    public ProgressBar setExtraMessage(String extraMessage) {
        return delegate.setExtraMessage(extraMessage);
    }

    @Override
    public ProgressBar setMax(int max) {
        return delegate.setMax(max);
    }

    @Override
    public void close() {
    }
}
