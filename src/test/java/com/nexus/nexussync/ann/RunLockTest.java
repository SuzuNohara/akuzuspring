package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link RunLock}: mutual exclusion through {@code nexussync/.run.lock} (A7). */
class RunLockTest {

  // U6-08
  @Test
  void given_freeDir_when_acquire_then_lockFileCreated(@TempDir Path dir) throws AnnException {
    try (RunLock lock = RunLock.acquire(dir)) {
      assertThat(lock.path()).isEqualTo(dir.resolve(RunLock.LOCK_FILE));
      assertThat(dir.resolve(".run.lock")).isRegularFile();
    }
  }

  // U6-08
  @Test
  void given_heldLock_when_acquireAgain_then_locked(@TempDir Path dir) throws AnnException {
    try (RunLock first = RunLock.acquire(dir)) {
      AnnException ex = catchThrowableOfType(() -> RunLock.acquire(dir), AnnException.class);

      assertThat(ex.kind()).isEqualTo(AnnException.Kind.LOCKED);
      assertThat(ex.getMessage()).contains(".run.lock");
      assertThat(first.path()).isRegularFile();
    }
  }

  // U6-08
  @Test
  void given_closedLock_when_acquireAgain_then_succeeds(@TempDir Path dir) throws AnnException {
    RunLock first = RunLock.acquire(dir);
    first.close();
    assertThat(dir.resolve(".run.lock")).doesNotExist();

    try (RunLock second = RunLock.acquire(dir)) {
      assertThat(second.path()).isRegularFile();
    }
    assertThat(dir.resolve(".run.lock")).doesNotExist();
  }

  // U6-08
  @Test
  void given_lockClosedTwice_when_close_then_idempotent(@TempDir Path dir) throws AnnException {
    RunLock lock = RunLock.acquire(dir);
    lock.close();

    lock.close();

    assertThat(dir.resolve(".run.lock")).doesNotExist();
  }

  // U6-08
  @Test
  void given_missingDir_when_acquire_then_notFound(@TempDir Path dir) {
    Path absent = dir.resolve("absent");

    AnnException ex = catchThrowableOfType(() -> RunLock.acquire(absent), AnnException.class);

    assertThat(ex.kind()).isEqualTo(AnnException.Kind.NOT_FOUND);
    assertThat(ex).hasCauseInstanceOf(IOException.class);
  }

  // U6-08
  @Test
  void given_lockReplacedByNonEmptyDir_when_close_then_uncheckedIoException(@TempDir Path dir)
      throws IOException, AnnException {
    RunLock lock = RunLock.acquire(dir);
    Files.delete(lock.path());
    Files.createDirectories(lock.path().resolve("child"));

    assertThatThrownBy(lock::close).isInstanceOf(UncheckedIOException.class);
  }
}
