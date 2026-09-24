package com.nexus.nexussync.ann;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ProcessLauncher} backed by {@link ProcessBuilder}.
 *
 * <p>Standard output and standard error are kept separate ({@code redirectErrorStream(false)}) and
 * drained concurrently so a chatty process never blocks on a full pipe. When {@code waitFor}
 * expires the process is destroyed forcibly and the result is flagged {@code timedOut}. This class
 * is excluded from coverage together with {@code cli.Main}: it is the only place that starts real
 * processes and is exercised by the {@code @arkannie} round trip, not by unit tests.
 */
public final class DefaultLauncher implements ProcessLauncher {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultLauncher.class);

  /** Time granted to the stream readers after the process ends (grandchildren may hold pipes). */
  private static final Duration DRAIN_GRACE = Duration.ofSeconds(5);

  private static final int READER_THREADS = 2;

  /**
   * Starts the command in {@code dir} with {@code env} added to the inherited environment.
   *
   * @implNote O(size of both streams) time and space; two reader threads live for the launch.
   */
  @Override
  public LaunchResult launch(List<String> cmd, Path dir, Map<String, String> env, Duration timeout)
      throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(cmd);
    builder.directory(dir.toFile());
    builder.redirectErrorStream(false);
    builder.environment().putAll(env);
    long start = System.nanoTime();
    Process process = builder.start();
    ExecutorService readers = Executors.newFixedThreadPool(READER_THREADS);
    try {
      Future<byte[]> out = readers.submit(() -> process.getInputStream().readAllBytes());
      Future<byte[]> err = readers.submit(() -> process.getErrorStream().readAllBytes());
      boolean timedOut = !await(process, cmd, timeout);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
      return LaunchResult.of(process.exitValue(), drain(out), drain(err), timedOut, elapsed);
    } finally {
      readers.shutdownNow();
    }
  }

  /** Waits for the process; destroys it on timeout. Returns {@code true} if it ended in time. */
  private static boolean await(Process process, List<String> cmd, Duration timeout)
      throws InterruptedException {
    if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      return true;
    }
    LOG.warn("process {} exceeded {} and was destroyed", cmd.get(0), timeout);
    process.destroyForcibly();
    process.waitFor();
    return false;
  }

  /** Collects a stream reader, giving up (with an empty string) if a pipe stays open. */
  private static String drain(Future<byte[]> reader) throws IOException, InterruptedException {
    try {
      return new String(
          reader.get(DRAIN_GRACE.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);
    } catch (ExecutionException e) {
      throw new IOException("cannot read process stream", e.getCause());
    } catch (TimeoutException e) {
      LOG.warn("process stream still open {} after termination; output discarded", DRAIN_GRACE);
      reader.cancel(true);
      return "";
    }
  }
}
