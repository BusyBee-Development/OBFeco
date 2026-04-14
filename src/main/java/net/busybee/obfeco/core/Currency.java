package net.busybee.obfeco.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Currency {
    private String id;
    private String displayName;
    private String symbol;
    private String format;
    private String material;
    private double startingBalance;
    private boolean useDecimals;
    private boolean notifyGive;
    private boolean notifyTake;

    public String getFormattedDisplayName() {
        return displayName.replace("&", "§");
    }
}
