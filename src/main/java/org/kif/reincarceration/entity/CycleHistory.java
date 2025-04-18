package org.kif.reincarceration.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class CycleHistory {
    private final Integer id;
    private final UUID playerId;
    private final String modifierId;
    private final Instant startTime;
    private final Instant endTime;
    private final boolean completed;
}
