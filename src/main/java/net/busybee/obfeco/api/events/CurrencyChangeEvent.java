package net.busybee.obfeco.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
@Setter
public class CurrencyChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final String currencyId;
    private final double oldBalance;
    private double newBalance;
    private final ChangeType changeType;
    private boolean cancelled;
    
    public CurrencyChangeEvent(Player player, String currencyId, double oldBalance, double newBalance, ChangeType changeType) {
        this.player = player;
        this.currencyId = currencyId;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.changeType = changeType;
        this.cancelled = false;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    
    public enum ChangeType {
        ADD,
        REMOVE,
        SET
    }
}