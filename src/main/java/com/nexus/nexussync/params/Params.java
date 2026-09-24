package com.nexus.nexussync.params;

import java.nio.file.Path;
import java.util.Set;

/**
 * Complete, immutable parameter set of an experiment (unit U1).
 *
 * @param experiment name of the experiment
 * @param seed seed of the single {@code Random} of a run
 * @param catalogDir directory with {@code activities.csv} and {@code activity_places.csv}
 * @param placesCsv path of {@code places.csv}
 * @param sampler sampler parameters
 * @param rounds gate parameters
 * @param agents agent parameters
 * @param decision decision parameters
 * @param place place assignment parameters
 * @param learning learning parameters
 * @param context context window parameters
 * @param runtime runtime parameters
 * @param rubric mediator rubric
 * @param bench bench parameters
 */
public record Params(
    String experiment,
    long seed,
    Path catalogDir,
    Path placesCsv,
    SamplerParams sampler,
    RoundsParams rounds,
    AgentsParams agents,
    DecisionParams decision,
    PlaceParams place,
    LearningParams learning,
    ContextParams context,
    RuntimeParams runtime,
    RubricParams rubric,
    BenchParams bench) {

  /**
   * Top-level sections left out of {@link #hash()} (deviation D-18): the whole {@code runtime}
   * section describes the machine (directories, binary, replay source, call budget, arkannie
   * version), not the experiment.
   */
  static final Set<String> HASH_EXCLUDED = Set.of("runtime");

  /**
   * Short, stable identifier of the EXPERIMENT: the first eight hex digits of the SHA-256 of the
   * canonical form of this record (sorted keys, no comments, see {@link CanonicalYaml}) without the
   * {@code runtime} section. Two loads of the same experiment on top of the same defaults yield the
   * same hash on any machine and from any checkout path, because {@code runtime.nexussync_dir},
   * {@code arkannie_bin}, {@code replay_dir}, {@code max_calls}, {@code arkannie_version} and
   * {@code executor} do not take part (D-18); learned weights keyed by this hash (A5) therefore
   * survive moving the tree. Any other value change, e.g. {@code sampler.eta}, changes the hash.
   *
   * @return eight lowercase hex characters
   * @implNote O(n log n) time and O(n) space in the number of parameters.
   */
  public String hash() {
    return CanonicalYaml.hash(this, HASH_EXCLUDED);
  }
}
