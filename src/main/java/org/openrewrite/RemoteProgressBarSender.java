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

import lombok.Value;
import org.openrewrite.internal.lang.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RemoteProgressBarSender implements ProgressBar {
    final static int MAX_MESSAGE_SIZE = 256;

    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;

    public RemoteProgressBarSender(int port) {
        this(null, port);
    }

    public RemoteProgressBarSender(@Nullable InetAddress address, int port) {
        try {
            String localhost = Files.exists(Paths.get("/.dockerenv")) ? "host.docker.internal" : "localhost";
            this.address = address == null ? InetAddress.getByName(localhost) : address;
            this.port = port;
            this.socket = new DatagramSocket();
        } catch (UnknownHostException | SocketException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void intermediateResult(@Nullable String message) {
        send(Request.Type.IntermediateResult, message);
    }

    @Override
    public void finish(String message) {
        throw new UnsupportedOperationException("The finish message must be determined by the receiver");
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public void step() {
        send(Request.Type.Step, null);
    }

    @Override
    public ProgressBar setExtraMessage(String extraMessage) {
        send(Request.Type.SetExtraMessage, extraMessage);
        return this;
    }

    @Override
    public ProgressBar setMax(int max) {
        send(Request.Type.SetMax, Integer.toString(max));
        return this;
    }

    private void send(Request.Type type, @Nullable String message) {
        try {
            if (message != null && message.length() + 1 > MAX_MESSAGE_SIZE) {
                throw new IllegalArgumentException("Message size exceeded maximum length: " + message);
            }
            byte[] buf = (type.ordinal() + (message == null ? "" : message)).getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Value
    static class Request {
        enum Type {
            IntermediateResult,
            Step,
            SetExtraMessage,
            SetMax
        }

        Type type;

        @Nullable
        String body;
    }
}
