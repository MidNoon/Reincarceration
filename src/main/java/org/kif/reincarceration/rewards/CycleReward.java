package org.kif.reincarceration.rewards;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Data @ToString
public class CycleReward {
    final List<CycleItem> items;
    final BigDecimal money;
    final List<String> commands;
    final List<String> commandDescriptions;
}
