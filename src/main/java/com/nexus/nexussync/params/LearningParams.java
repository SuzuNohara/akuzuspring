package com.nexus.nexussync.params;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters of the weight learning (unit U10).
 *
 * <p>The design names {@code wMin} and {@code wMax} are exposed as {@code weightMin} and {@code
 * weightMax} because Google style forbids a one-letter camel-case prefix; the YAML keys stay {@code
 * w_min} and {@code w_max}.
 *
 * @param eta learning rate for neutral ratings
 * @param etaPos learning rate for positive ratings
 * @param etaNeg learning rate for negative ratings
 * @param coldStartMin rated dates required before learned weights are used
 * @param weightMin lower clamp of every weight ({@code w_min}, A4)
 * @param weightMax upper clamp of every weight ({@code w_max})
 */
public record LearningParams(
    double eta,
    double etaPos,
    double etaNeg,
    int coldStartMin,
    @JsonProperty("w_min") double weightMin,
    @JsonProperty("w_max") double weightMax) {}
