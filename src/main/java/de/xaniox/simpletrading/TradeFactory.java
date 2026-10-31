/*
 * This file is part of SimpleTrading.
 * Copyright (c) 2015-2016 Matthias Werning
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.xaniox.simpletrading;

import de.xaniox.simpletrading.Trade.StopCause;
import de.xaniox.simpletrading.config.ItemControlManager;
import de.xaniox.simpletrading.config.TradeConfiguration;
import de.xaniox.simpletrading.config.WorldControlManager;
import de.xaniox.simpletrading.i18n.I18N;
import de.xaniox.simpletrading.i18n.I18NManager;
import de.xaniox.simpletrading.i18n.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class TradeFactory implements Listener {

    private final I18N i18n = I18NManager.getGlobal();
    private final SimpleTrading plugin;
    private final TradeConfiguration config;
    private final Set<Trade> trades;
    private final ItemControlManager controlManager;

    public TradeFactory(SimpleTrading plugin, TradeConfiguration config, ItemControlManager controlManager) {
        this.plugin = plugin;
        this.config = config;
        this.trades = new LinkedHashSet<>();
        this.controlManager = controlManager;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Trade initiateTrade(Player initiator, Player partner) {
        if (isInvolvedInTrade(initiator)) {
            return null;
        }

        if (isInvolvedInTrade(partner)) {
            initiator.sendMessage(i18n.getVarString(Messages.General.PARTNER_ALREADY_INVOLVED)
                .setVariable("player", partner.getName())
                .toComponent());
            return null;
        }

        DefaultTrade trade = new DefaultTrade(initiator, partner, config, controlManager, plugin);

        long timeoutTicks = config.getTimeout() * 20L;
        final BukkitTask timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> timeoutTrade(trade), timeoutTicks);

        trade.setListener((changed, newState) -> {
            switch (newState) {
                case CANCELLED, CONTRACTED -> {
                    trades.remove(changed);
                    timeoutTask.cancel();
                }
                case TRADING -> timeoutTask.cancel();
                default -> {
                }
            }
        });

        trades.add(trade);

        initiator.sendMessage(i18n.getVarString(Messages.General.TRADE_REQUESTED)
            .setVariable("player", partner.getName())
            .toComponent());
        partner.sendMessage(i18n.getVarString(Messages.General.TRADE_REQUEST_RECEIVED)
            .setVariable("player", initiator.getName())
            .toComponent());

        return trade;
    }

    public void declineTrade(Player decliner) {
        Trade trade = getTrade(decliner);
        if (trade == null) {
            throw new IllegalStateException(decliner.getName() + " is not involved in any trade. Can not decline.");
        }

        if (trade.getState() != TradeState.REQUESTED) {
            return;
        }

        trade.setState(TradeState.CANCELLED);
        trades.remove(trade);

        Player initiator = trade.getInitiator().getPlayer();
        initiator.sendMessage(i18n.getVarString(Messages.General.TRADE_REQUEST_DECLINED)
            .setVariable("player", decliner.getName())
            .toComponent());
    }

    private void timeoutTrade(Trade trade) {
        if (trade.getState() != TradeState.REQUESTED) {
            return;
        }

        trades.remove(trade);
        trade.stop(StopCause.TIMEOUT, trade.getInitiator());
    }

    public void acceptTrade(Player accepter) {
        Trade trade = getTrade(accepter);
        if (trade == null) {
            throw new IllegalStateException(accepter.getName() + " is not involved in any trade. Can not accept.");
        }

        if (trade.getState() != TradeState.REQUESTED) {
            return;
        }

        Player initiator = trade.getInitiator().getPlayer();
        initiator.sendMessage(i18n.getVarString(Messages.General.TRADE_ACCEPTED)
            .setVariable("player", accepter.getName())
            .toComponent());

        trade.accept();
    }

    public void stopTrade(Trade trade, StopCause cause, Player who) {
        if (!trades.contains(trade)) {
            return;
        }

        TradePlayer tradePlayer;
        if (trade.getInitiator().getPlayer() == who) {
            tradePlayer = trade.getInitiator();
        } else if (trade.getPartner().getPlayer() == who) {
            tradePlayer = trade.getPartner();
        } else {
            throw new IllegalArgumentException(who.getName() + " is not affiliated in this trade");
        }

        trade.stop(cause, tradePlayer);
        trades.remove(trade);
    }

    public void stopAllTrades(StopCause cause) {
        // Stopping a trade removes it from the set, so iterate over a copy
        for (Trade trade : new ArrayList<>(trades)) {
            trade.stop(cause, trade.getInitiator());
        }

        trades.clear();
    }

    public boolean isInvolvedInTrade(Player player) {
        return getTrade(player) != null;
    }

    public Trade getTrade(Player player) {
        for (Trade trade : trades) {
            if (trade.getInitiator().getPlayer() == player || trade.getPartner().getPlayer() == player) {
                return trade;
            }
        }

        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!config.usesShiftTrading() || event.getHand() != EquipmentSlot.HAND) {
            // The event fires once per hand, only handle the main hand
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!(event.getRightClicked() instanceof Player tradePartner) || tradePartner.hasMetadata("NPC")) {
            return;
        }

        if (!config.allowsCreativeTrading()
                && (player.getGameMode() == GameMode.CREATIVE || tradePartner.getGameMode() == GameMode.CREATIVE)) {
            player.sendMessage(i18n.getVarString(Messages.General.PARTNER_IN_CREATIVE)
                .setVariable("player", tradePartner.getName())
                .toComponent());
            return;
        }

        WorldControlManager worldControlManager = plugin.getWorldControlManager();
        if (!worldControlManager.isAllowed(player.getWorld())) {
            player.sendMessage(i18n.get(Messages.Command.CANNOT_TRADE_IN_WORLD));
            return;
        }

        Trade trade = getTrade(player);
        if (trade == null) {
            if (!player.hasPermission(Permissions.TRADE_INITIATE_SHIFT.getPermission())) {
                player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
                return;
            }

            initiateTrade(player, tradePartner);
        } else if (trade.getPartner().getPlayer() == player && trade.getInitiator().getPlayer() == tradePartner) {
            if (!player.hasPermission(Permissions.TRADE_ACCEPT_SHIFT.getPermission())) {
                player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
                return;
            }

            acceptTrade(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Trade trade = getTrade(player);
        if (trade == null || trade.getState() != TradeState.TRADING) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Trade trade = getTrade(player);
        if (trade == null || trade.getState() != TradeState.TRADING) {
            return;
        }

        stopTrade(trade, StopCause.INVENTORY_CLOSE, player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Trade trade = getTrade(player);

        if (trade == null) {
            return;
        }

        stopTrade(trade, StopCause.QUIT, player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Trade trade = getTrade(player);

        if (trade == null || trade.getState() != TradeState.TRADING) {
            return;
        }

        stopTrade(trade, StopCause.DEATH, player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean isTradeInventory = event.getView().getTopInventory().getHolder(false) instanceof TradeInventoryHolder;
        Trade trade = getTrade(player);

        if (trade == null || trade.getState() != TradeState.TRADING) {
            if (isTradeInventory) {
                // A trade inventory that no longer belongs to a running trade must never hand out items
                event.setCancelled(true);
            }
            return;
        }

        trade.onInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean isTradeInventory = event.getView().getTopInventory().getHolder(false) instanceof TradeInventoryHolder;
        Trade trade = getTrade(player);

        if (isTradeInventory || (trade != null && trade.getState() == TradeState.TRADING)) {
            event.setCancelled(true);
        }
    }

}
