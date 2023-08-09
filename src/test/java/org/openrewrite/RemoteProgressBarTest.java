package org.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.internal.lang.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
