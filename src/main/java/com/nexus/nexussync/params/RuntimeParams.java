package com.nexus.nexussync.params;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Runtime location and limits of a run (unit U6).
 *
 * @param executor kind of gate executor
 * @param nexussyncDir root of {@code ann/}, {@code .agents/}, {@code params/}, {@code fixtures/},
 *     {@code calibration/} and {@code runs/}; it is also {@code ARKANNIE_HOME}
 * @param arkannieBin arkannie binary, relative to {@code nexussyncDir} when not absolute
 * @param replayDir directory with recorded outputs for the REPLAY executor
 * @param maxCalls maximum number of agent calls per run (A2)
 * @param arkannieVersion expected version reported by {@code arkannie --version} (A3)
 */
public record RuntimeParams(
    ExecutorKind executor,
    Path nexussyncDir,
    Path arkannieBin,
    Optional<Path> replayDir,
    int maxCalls,
    String arkannieVersion) {}
