package com.nexus.nexussync.ann;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mutual exclusion between runs that share one {@code nexussync/} directory (A7).
 *
 * <p>The lock is the atomic creation of {@code nexussync/.run.lock}: a second {@link
 * #acquire(Path)} while the file exists fails with {@code AnnException(LOCKED)}, and {@link
 * #close()} deletes it. Use it in a try-with-resources around the whole run. A stale file left by a
 * crashed process must be removed by hand; the lock never steals it.
 */
public final class RunLock implements AutoCloseable {

  /** Name of the lock file inside the nexussync directory. */
  public static final String LOCK_FILE = ".run.lock";

  private final Path lockFile;

  private RunLock(Path lockFile) {
    this.lockFile = lockFile;
  }

  /**
   * Creates {@code nexussyncDir/.run.lock} atomically and returns the lock that owns it.
   *
   * @param nexussyncDir existing nexussync directory (also {@code ARKANNIE_HOME})
   * @return the acquired lock; closing it releases the file
   * @throws AnnException {@code LOCKED} if the lock file already exists; {@code NOT_FOUND} if the
   *     directory does not exist or the file cannot be created
   * @implNote O(1) time and space: one file-system operation.
   */
  public static RunLock acquire(Path nexussyncDir) throws AnnException {
    Path lockFile = nexussyncDir.resolve(LOCK_FILE);
    try {
      Files.createFile(lockFile);
    } catch (FileAlreadyExistsException e) {
      throw new AnnException(
          AnnException.Kind.LOCKED, "another run holds " + lockFile + "; remove it if stale", e);
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.NOT_FOUND, "cannot create " + lockFile, e);
    }
    return new RunLock(lockFile);
  }

  /**
   * Returns the lock file this instance owns.
   *
   * @return the absolute or relative path given to {@link #acquire(Path)} plus {@value #LOCK_FILE}
   * @implNote O(1) time and space.
   */
  public Path path() {
    return lockFile;
  }

  /**
   * Deletes the lock file; calling it again after a successful release is a no-op.
   *
   * @throws UncheckedIOException if the file exists but cannot be deleted
   * @implNote O(1) time and space: one file-system operation.
   */
  @Override
  public void close() {
    try {
      Files.deleteIfExists(lockFile);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot release " + lockFile, e);
    }
  }
}
