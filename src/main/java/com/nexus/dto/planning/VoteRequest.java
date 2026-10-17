package com.nexus.dto.planning;

import com.nexus.model.Vote;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull Vote vote) {}
