package net.busybee.obfeco.migration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CurrencyScanResult {
    private final String id;
    private final String name;
    private final String symbol;
    private final double startingBalance;
    private final boolean useDecimals;
    private final boolean alreadyExists;
    private final int playerCount;
}
