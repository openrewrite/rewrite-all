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

import org.junit.jupiter.api.Test;
import org.openrewrite.internal.lang.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @deprecated This class has been moved to rewrite-polyglot.
 */
@Deprecated
public class RemoteProgressBarTest {

    @Test
    void remote() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(4);
        try (
          ProgressBar progressBar = new ProgressBar() {
              @Override
              public void intermediateResult(@Nullable String message) {
                  assertThat(message).isEqualTo("intermediate");
                  latch.countDown();
              }

              @Override
              public void finish(String message) {
              }

              @Override
              public void close() {
              }

              @Override
              public void step() {
                  latch.countDown();
              }

              @Override
              public ProgressBar setExtraMessage(String extraMessage) {
                  assertThat(extraMessage).isEqualTo("extra");
                  latch.countDown();
                  return this;
              }

              @Override
              public ProgressBar setMax(int max) {
                  assertThat(max).isEqualTo(100);
                  latch.countDown();
                  return this;
              }
          };

          RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(progressBar)) {
            try (ProgressBar sender = new RemoteProgressBarSender(receiver.getPort())) {
                sender.setMax(100);
                sender.step();
                sender.setExtraMessage("extra");
                sender.intermediateResult("intermediate");
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
