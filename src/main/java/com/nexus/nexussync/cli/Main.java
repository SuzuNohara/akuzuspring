package com.nexus.nexussync.cli;

import com.nexus.nexussync.ann.DefaultLauncher;
import com.nexus.nexussync.ann.ProcessLauncher;

/**
 * Entry point of the nexussync CLI (unit U11, §3.11): parses the arguments with {@link ArgParser}
 * and hands them to {@link Commands}. Exit code 0 ok, 1 error, 2 usage. It prints nothing itself
 * (SLF4J and files only) and is excluded from JaCoCo.
 */
public final class Main {

  private Main() {}

  /**
   * Runs one CLI command and exits with its code.
   *
   * @param args command and options, see {@link ArgParser#USAGE}
   * @implNote O(1) time and space besides the command itself.
   */
  public static void main(String[] args) {
    ProcessLauncher launcher = new DefaultLauncher();
    Commands commands = new Commands(launcher, GateExecutorFactory.defaults(launcher));
    System.exit(commands.execute(ArgParser.parse(args)));
  }
}
