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

import de.xaniox.simpletrading.config.ItemControlManager;
import de.xaniox.simpletrading.config.TradeConfiguration;
import de.xaniox.simpletrading.i18n.I18N;
import de.xaniox.simpletrading.i18n.I18NManager;
import de.xaniox.simpletrading.i18n.Messages;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultTrade implements Trade {

    private static final int INVENTORY_SIZE = 6 * 9;
    private static final int[] SEPERATOR_INDEXES = { 0, 1, 7, 8, 9, 13, 17, 22, 31, 40, 49 };

    private static final Material UNCONFIRMED_STATUS_MATERIAL = Material.RED_STAINED_GLASS;
    private static final Material CONFIRMED_STATUS_MATERIAL = Material.LIME_STAINED_GLASS;

    private static final int EXP_INFO_INDEX = 6;
    private static final int ACCEPT_TRADE_INDEX = 3;
    private static final int CONFIRMATION_INFO_INDEX = 4;
    private static final int DECLINE_TRADE_INDEX = 5;
    private static final int MONEY_INFO_INDEX = 2;
    private static final int ADD_MONEY_1_INDEX = 10;
    private static final int ADD_MONEY_2_INDEX = 11;
    private static final int ADD_MONEY_3_INDEX = 12;
    private static final int ADD_EXP_1_INDEX = 14;
    private static final int ADD_EXP_2_INDEX = 15;
    private static final int ADD_EXP_3_INDEX = 16;

    private static final float ADD_PITCH = 1.5F;
    private static final float REMOVE_PITCH = 1.0F;
    private static final DecimalFormat LEVEL_FORMAT = new DecimalFormat("0");

    static {
        LEVEL_FORMAT.setPositivePrefix("+");
    }

    private final TradePlayer initiator;
    private final TradePlayer partner;

    private final SimpleTrading plugin;
    private final TradeConfiguration config;
    private final I18N i18n = I18NManager.getGlobal();
    private final Economy econ;
    private final ItemControlManager controlManager;

    private StateChangedListener listener;
    private TradeState state;

    public DefaultTrade(Player initiator, Player partner, TradeConfiguration config,
            ItemControlManager controlManager, SimpleTrading plugin) {
        this.plugin = plugin;
        this.initiator = new TradePlayer(initiator);
        this.partner = new TradePlayer(partner);
        this.config = config;
        this.econ = plugin.getEconomy();
        this.controlManager = controlManager;
        this.state = TradeState.REQUESTED;
    }

    @Override
    public TradePlayer getInitiator() {
        return initiator;
    }

    @Override
    public TradePlayer getPartner() {
        return partner;
    }

    @Override
    public TradeState getState() {
        return state;
    }

    public void setListener(StateChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public void setState(TradeState state) {
        this.state = state;

        if (listener != null) {
            listener.onStateChanged(this, state);
        }
    }

    private boolean usesMoney() {
        return econ != null && config.usesMoneyTrading();
    }

    @Override
    public void accept() {
        Inventory initiatorInventory = createTradeInventory(config.getInventoryName(partner.getName()));
        Inventory partnerInventory = createTradeInventory(config.getInventoryName(initiator.getName()));

        initializeInventory(initiatorInventory);
        initializeInventory(partnerInventory);

        initiator.setInventory(initiatorInventory);
        partner.setInventory(partnerInventory);

        // Open the inventories before switching to TRADING, so that closing any
        // previously opened inventory isn't treated as leaving the trade
        initiator.getPlayer().openInventory(initiatorInventory);
        partner.getPlayer().openInventory(partnerInventory);

        setState(TradeState.TRADING);
    }

    private static Inventory createTradeInventory(String title) {
        TradeInventoryHolder holder = new TradeInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Text.of(title));
        holder.setInventory(inventory);
        return inventory;
    }

    private static ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore != null) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createSeperator() {
        ItemStack seperator = new ItemStack(config.getSeperatorMaterial());
        ItemMeta seperatorMeta = seperator.getItemMeta();
        seperatorMeta.setHideTooltip(true);
        seperator.setItemMeta(seperatorMeta);
        return seperator;
    }

    private Component offerLine(TradePlayer player, String offer) {
        return Text.item(i18n.getVarString(Messages.Inventory.OFFER_LORE)
            .setVariable("player", player.getName())
            .setVariable("offer", offer)
            .toString());
    }

    private ItemStack createMoneyInfo() {
        return createItem(config.getMoneyStatusMaterial(), Text.item(i18n.getString(Messages.Inventory.MONEY_INFO_TITLE)), List.of(
            offerLine(initiator, econ.format(initiator.getMoneyOffer())),
            offerLine(partner, econ.format(partner.getMoneyOffer()))));
    }

    private ItemStack createAddMoneyButton(int value) {
        return createItem(config.getMoneyAddRemoveMaterial(),
            Text.item(i18n.getVarString(Messages.Inventory.ADD_REMOVE_MONEY_LORE)
                .setVariable("money", econ.format(value))
                .toString()),
            Text.lore(i18n.getString(Messages.Inventory.ADD_MONEY_LORE)));
    }

    private ItemStack createAddExpButton(int value) {
        return createItem(config.getXpAddRemoveMaterial(),
            Text.item(i18n.getVarString(Messages.Inventory.ADD_EXP_TITLE)
                .setVariable("exp", String.valueOf(value))
                .toString()),
            Text.lore(i18n.getVarString(Messages.Inventory.ADD_EXP_LORE)
                .setVariable("exp", String.valueOf(value))
                .toString()));
    }

    private ItemStack createStatusItem(boolean confirmed) {
        String loreLine = i18n.getString(confirmed ? Messages.Inventory.ONE_PLAYER_ACCEPTED : Messages.Inventory.WAITING_FOR_OTHER_PLAYER_LORE);

        return createItem(confirmed ? CONFIRMED_STATUS_MATERIAL : UNCONFIRMED_STATUS_MATERIAL,
            Text.item(i18n.getVarString(Messages.Inventory.TRADE_STATUS_TITLE)
                .setVariable("color", confirmed ? "&a" : "&c")
                .toString()),
            List.of(Text.item("&f" + loreLine)));
    }

    private void initializeInventory(Inventory inv) {
        ItemStack seperator = createSeperator();

        for (int seperatorIndex : SEPERATOR_INDEXES) {
            inv.setItem(seperatorIndex, seperator);
        }

        ItemStack expInfoItemStack = seperator;
        ItemStack addExp1ItemStack = seperator;
        ItemStack addExp2ItemStack = seperator;
        ItemStack addExp3ItemStack = seperator;

        if (config.usesXpTrading()) {
            expInfoItemStack = createItem(config.getXpStatusMaterial(), Text.item(i18n.getString(Messages.Inventory.EXP_INFO_TITLE)), List.of(
                offerLine(initiator, "0"),
                offerLine(partner, "0")));

            addExp1ItemStack = createAddExpButton(config.getExpValue1());
            addExp2ItemStack = createAddExpButton(config.getExpValue2());
            addExp3ItemStack = createAddExpButton(config.getExpValue3());
        }

        ItemStack acceptItemStack = createItem(config.getAcceptMaterial(), Text.item(i18n.getString(Messages.Inventory.ACCEPT_TRADE_TITLE)), null);
        ItemStack declineItemStack = createItem(config.getDeclineMaterial(), Text.item(i18n.getString(Messages.Inventory.DECLINE_TRADE_TITLE)), null);

        ItemStack moneyInfoItemStack = seperator;
        ItemStack addMoney1ItemStack = seperator;
        ItemStack addMoney2ItemStack = seperator;
        ItemStack addMoney3ItemStack = seperator;

        if (usesMoney()) {
            moneyInfoItemStack = createMoneyInfo();
            addMoney1ItemStack = createAddMoneyButton(config.getMoneyValue1());
            addMoney2ItemStack = createAddMoneyButton(config.getMoneyValue2());
            addMoney3ItemStack = createAddMoneyButton(config.getMoneyValue3());
        }

        inv.setItem(EXP_INFO_INDEX, expInfoItemStack);
        inv.setItem(ACCEPT_TRADE_INDEX, acceptItemStack);
        inv.setItem(CONFIRMATION_INFO_INDEX, createStatusItem(false));
        inv.setItem(DECLINE_TRADE_INDEX, declineItemStack);
        inv.setItem(MONEY_INFO_INDEX, moneyInfoItemStack);
        inv.setItem(ADD_MONEY_1_INDEX, addMoney1ItemStack);
        inv.setItem(ADD_MONEY_2_INDEX, addMoney2ItemStack);
        inv.setItem(ADD_MONEY_3_INDEX, addMoney3ItemStack);
        inv.setItem(ADD_EXP_1_INDEX, addExp1ItemStack);
        inv.setItem(ADD_EXP_2_INDEX, addExp2ItemStack);
        inv.setItem(ADD_EXP_3_INDEX, addExp3ItemStack);
    }

    @Override
    public void stop(StopCause cause, TradePlayer who) {
        TradePlayer other = who == initiator ? partner : initiator;

        TradeState previousState = getState();
        setState(TradeState.CANCELLED);

        if (previousState == TradeState.TRADING) {
            reclaimItems(initiator);
            reclaimItems(partner);

            initiator.getPlayer().closeInventory();
            partner.getPlayer().closeInventory();

            if (!plugin.isDisabling()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    initiator.getPlayer().updateInventory();
                    partner.getPlayer().updateInventory();
                });
            }
        }

        switch (cause) {
            case DEATH -> other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_DEATH)
                .setVariable("player", who.getName())
                .toComponent());
            case INVENTORY_CLOSE, DECLINE -> {
                other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_CANCEL)
                    .setVariable("player", who.getName())
                    .toComponent());
                who.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_DECLINE)
                    .setVariable("player", other.getName())
                    .toComponent());
            }
            case LEFT_WORLD -> other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_LEFT_WORLD)
                .setVariable("player", who.getName())
                .toComponent());
            case MOVE -> other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_MOVED_AWAY)
                .setVariable("player", who.getName())
                .toComponent());
            case QUIT -> other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_PLAYER_LEFT)
                .setVariable("player", who.getName())
                .toComponent());
            case TIMEOUT -> {
                who.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_TIMEOUT)
                    .setVariable("player", other.getName())
                    .toComponent());
                other.getPlayer().sendMessage(i18n.getVarString(Messages.General.CANCEL_TRADE_TIMEOUT)
                    .setVariable("player", who.getName())
                    .toComponent());
            }
            case SERVER_SHUTDOWN -> {
                who.getPlayer().sendMessage(i18n.get(Messages.General.CANCEL_SERVER_SHUTDOWN));
                other.getPlayer().sendMessage(i18n.get(Messages.General.CANCEL_SERVER_SHUTDOWN));
            }
        }
    }

    private void reclaimItems(TradePlayer player) {
        Inventory inv = player.getInventory();
        if (inv == null) {
            return;
        }

        for (int y = 2; y < 6; y++) {
            for (int x = 0; x < 4; x++) {
                int slot = y * 9 + x;

                ItemStack current = inv.getItem(slot);
                inv.setItem(slot, null);

                if (current != null && !current.isEmpty()) {
                    giveOrDrop(player.getPlayer(), current);
                }
            }
        }
    }

    /**
     * Adds the item to the players inventory and drops whatever doesn't fit.
     *
     * @return true if everything fit into the inventory
     */
    private static boolean giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> untransferred = player.getInventory().addItem(stack);
        for (ItemStack rest : untransferred.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }

        return untransferred.isEmpty();
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        TradePlayer tradePlayer = initiator.getPlayer() == player ? initiator : partner.getPlayer() == player ? partner : null;
        if (tradePlayer == null) {
            //This event does not belong to this trade
            return;
        }

        // Never let the vanilla click logic run while trading
        event.setCancelled(true);

        SlotType type = event.getSlotType();
        if (type != SlotType.CONTAINER && type != SlotType.QUICKBAR) {
            return;
        }

        InventoryView view = event.getView();
        Inventory inventory = event.getClickedInventory();
        if (inventory == null) {
            return;
        }

        boolean isPlayerInventory = inventory.getType() == InventoryType.PLAYER;
        if (!isPlayerInventory && inventory != view.getTopInventory()) {
            return;
        }

        ClickType clickType = event.getClick();
        int slot = event.getSlot();
        TradeAction action = TradeAction.NOTHING;

        int moneyAdding = 0;
        int expAdding = 0;

        if (isPlayerInventory) {
            action = TradeAction.MOVE_ITEM_TO_TRADE_INVENTORY;
        } else if (slot == ADD_MONEY_1_INDEX) {
            moneyAdding = config.getMoneyValue1();
            action = TradeAction.ADD_MONEY;
        } else if (slot == ADD_MONEY_2_INDEX) {
            moneyAdding = config.getMoneyValue2();
            action = TradeAction.ADD_MONEY;
        } else if (slot == ADD_MONEY_3_INDEX) {
            moneyAdding = config.getMoneyValue3();
            action = TradeAction.ADD_MONEY;
        } else if (slot == ADD_EXP_1_INDEX && config.usesXpTrading()) {
            expAdding = config.getExpValue1();
            action = TradeAction.ADD_EXP;
        } else if (slot == ADD_EXP_2_INDEX && config.usesXpTrading()) {
            expAdding = config.getExpValue2();
            action = TradeAction.ADD_EXP;
        } else if (slot == ADD_EXP_3_INDEX && config.usesXpTrading()) {
            expAdding = config.getExpValue3();
            action = TradeAction.ADD_EXP;
        } else if (slot == ACCEPT_TRADE_INDEX) {
            action = TradeAction.ACCEPT;
        } else if (slot == DECLINE_TRADE_INDEX) {
            action = TradeAction.DECLINE;
        } else if (slot % 9 < 4 && slot / 9 > 1) {
            action = TradeAction.MOVE_ITEM_TO_PLAYER_INVENTORY;
        }

        //Process the calculated data
        switch (action) {
            case ACCEPT -> {
                tradePlayer.setAccepted(true);

                if (initiator.hasAccepted() && partner.hasAccepted()) {
                    if (canAffordOffers()) {
                        contractTrade();
                        return;
                    }

                    declineAll();
                }
            }
            case DECLINE -> {
                tradePlayer.setAccepted(false);

                if (config.isAbortOnDecline()) {
                    plugin.getFactory().stopTrade(this, StopCause.DECLINE, player);
                    return;
                }
            }
            case ADD_EXP -> {
                int newExpOffer = tradePlayer.getExpOffer();

                if (clickType == ClickType.LEFT) {
                    newExpOffer += expAdding;

                    if (newExpOffer > SetExpFix.getTotalExperience(player)) {
                        // Too few exp
                        player.sendMessage(i18n.get(Messages.General.NOT_ENOUGH_XP));
                        return;
                    }

                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, ADD_PITCH);
                } else if (clickType == ClickType.RIGHT) {
                    newExpOffer -= expAdding;

                    if (newExpOffer < 0) {
                        player.sendMessage(i18n.get(Messages.General.NO_XP_OFFER));
                        return;
                    }

                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, REMOVE_PITCH);
                } else {
                    return;
                }

                tradePlayer.setExpOffer(newExpOffer);
                declineAll();
            }
            case ADD_MONEY -> {
                if (!usesMoney()) {
                    return;
                }

                int newMoneyOffer = tradePlayer.getMoneyOffer();
                int maxMoneyAmount = config.getMaxMoneyTrading();

                if (clickType == ClickType.LEFT) {
                    newMoneyOffer += moneyAdding;

                    if (!econ.has(player, newMoneyOffer)) {
                        // Not enough money
                        player.sendMessage(i18n.get(Messages.General.NOT_ENOUGH_MONEY));
                        return;
                    }

                    if (newMoneyOffer > maxMoneyAmount && maxMoneyAmount != -1) {
                        // Reached the limit
                        player.sendMessage(i18n.get(Messages.General.MAX_TRADE_AMOUNT_REACHED));
                        return;
                    }

                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, ADD_PITCH);
                } else if (clickType == ClickType.RIGHT) {
                    newMoneyOffer -= moneyAdding;

                    if (newMoneyOffer < 0) {
                        player.sendMessage(i18n.get(Messages.General.NO_NEGATIVE_MONEY_OFFER));
                        return;
                    }

                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, REMOVE_PITCH);
                } else {
                    return;
                }

                tradePlayer.setMoneyOffer(newMoneyOffer);
                declineAll();
            }
            case MOVE_ITEM_TO_PLAYER_INVENTORY, MOVE_ITEM_TO_TRADE_INVENTORY -> {
                if (!moveItem(event, tradePlayer, action, clickType)) {
                    return;
                }
            }
            case NOTHING -> {
            }
        }

        updateInventoryStatus();
    }

    /**
     * Moves the clicked item between the player inventory and the trade inventory.
     *
     * @return true if something was moved
     */
    private boolean moveItem(InventoryClickEvent event, TradePlayer tradePlayer, TradeAction action, ClickType clickType) {
        Player player = tradePlayer.getPlayer();
        ItemStack stack = event.getCurrentItem();

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (!controlManager.isAllowed(stack)) {
            // This item is not tradeable
            player.sendMessage(i18n.get(Messages.General.CANNOT_TRADE_ITEM));
            return false;
        }

        ItemStack stackClone = stack.clone();
        int newStackAmount;

        if (clickType == ClickType.LEFT) {
            newStackAmount = 0;
        } else if (clickType == ClickType.RIGHT) {
            stackClone.setAmount(1);
            newStackAmount = stack.getAmount() - 1;
        } else {
            return false;
        }

        int untransferred;
        if (action == TradeAction.MOVE_ITEM_TO_TRADE_INVENTORY) {
            untransferred = addToTradeInventory(tradePlayer, stackClone);
        } else {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(stackClone);
            untransferred = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
        }

        int remaining = newStackAmount + untransferred;
        if (remaining <= 0) {
            event.setCurrentItem(null);
        } else {
            stack.setAmount(remaining);
            event.setCurrentItem(stack);
        }

        declineAll();
        reflectChanges(tradePlayer);
        return true;
    }

    private void declineAll() {
        initiator.setAccepted(false);
        partner.setAccepted(false);
    }

    /**
     * Re-checks the offers right before the trade is executed, since balances
     * and experience may have changed since the offer was made.
     */
    private boolean canAffordOffers() {
        for (TradePlayer tradePlayer : List.of(initiator, partner)) {
            Player player = tradePlayer.getPlayer();

            if (tradePlayer.getMoneyOffer() > 0 && (!usesMoney() || !econ.has(player, tradePlayer.getMoneyOffer()))) {
                initiator.getPlayer().sendMessage(i18n.get(Messages.General.NOT_ENOUGH_MONEY));
                partner.getPlayer().sendMessage(i18n.get(Messages.General.NOT_ENOUGH_MONEY));
                return false;
            }

            if (tradePlayer.getExpOffer() > SetExpFix.getTotalExperience(player)) {
                initiator.getPlayer().sendMessage(i18n.get(Messages.General.NOT_ENOUGH_XP));
                partner.getPlayer().sendMessage(i18n.get(Messages.General.NOT_ENOUGH_XP));
                return false;
            }
        }

        return true;
    }

    private void transferMoney(Player from, Player to, int amount) {
        if (amount <= 0) {
            return;
        }

        EconomyResponse response = econ.withdrawPlayer(from, amount);
        if (response.transactionSuccess()) {
            econ.depositPlayer(to, amount);
        } else {
            plugin.getLogger().warning("Could not withdraw " + amount + " from " + from.getName() + ": " + response.errorMessage);
        }
    }

    private static void transferExp(Player from, Player to, int amount) {
        if (amount <= 0) {
            return;
        }

        SetExpFix.setTotalExperience(from, SetExpFix.getTotalExperience(from) - amount);
        SetExpFix.setTotalExperience(to, SetExpFix.getTotalExperience(to) + amount);
    }

    private void contractTrade() {
        Player initiatorPlayer = initiator.getPlayer();
        Player partnerPlayer = partner.getPlayer();

        if (usesMoney()) {
            transferMoney(initiatorPlayer, partnerPlayer, initiator.getMoneyOffer());
            transferMoney(partnerPlayer, initiatorPlayer, partner.getMoneyOffer());
        }

        transferExp(initiatorPlayer, partnerPlayer, initiator.getExpOffer());
        transferExp(partnerPlayer, initiatorPlayer, partner.getExpOffer());

        setState(TradeState.CONTRACTED);

        transferTradeItems(initiator, partner);
        transferTradeItems(partner, initiator);

        initiatorPlayer.closeInventory();
        partnerPlayer.closeInventory();

        initiatorPlayer.updateInventory();
        partnerPlayer.updateInventory();

        initiatorPlayer.playSound(initiatorPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        partnerPlayer.playSound(partnerPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);

        initiatorPlayer.sendMessage(i18n.getVarString(Messages.General.TRADE_CONFIRMED)
            .setVariable("player", partnerPlayer.getName())
            .toComponent());
        partnerPlayer.sendMessage(i18n.getVarString(Messages.General.TRADE_CONFIRMED)
            .setVariable("player", initiator.getName())
            .toComponent());
    }

    private void transferTradeItems(TradePlayer from, TradePlayer to) {
        Inventory inv = from.getInventory();

        boolean hasUntransferredItems = false;
        for (int y = 2; y < 6; y++) {
            for (int x = 0; x < 4; x++) {
                int slot = y * 9 + x;

                ItemStack current = inv.getItem(slot);
                // Clear the slot so the item can never be taken out of the trade inventory again
                inv.setItem(slot, null);

                if (current == null || current.isEmpty()) {
                    continue;
                }

                if (!giveOrDrop(to.getPlayer(), current)) {
                    hasUntransferredItems = true;
                }
            }
        }

        if (hasUntransferredItems) {
            to.getPlayer().sendMessage(i18n.get(Messages.General.INVENTORY_FULL_ITEMS_DROPPED));
        }
    }

    private void reflectChanges(TradePlayer player) {
        Inventory inv = player.getInventory();
        Inventory otherInv = player == initiator ? partner.getInventory() : initiator.getInventory();

        for (int y = 2; y < 6; y++) {
            for (int x = 0; x < 4; x++) {
                int slot = y * 9 + x;
                int reflectedSlot = y * 9 + (8 - x);

                ItemStack current = inv.getItem(slot);
                otherInv.setItem(reflectedSlot, current == null ? null : current.clone());
            }
        }
    }

    private int addToTradeInventory(TradePlayer player, ItemStack stack) {
        Inventory inv = player.getInventory();
        int maxStackSize = Math.max(stack.getAmount(), stack.getMaxStackSize());

        for (int y = 2; y < 6; y++) {
            for (int x = 0; x < 4; x++) {
                int slot = y * 9 + x;

                ItemStack current = inv.getItem(slot);
                int amount;
                if (current != null && !current.isEmpty() && !current.isSimilar(stack)) {
                    //Totally not the itemstack we're searching for
                    continue;
                } else if (current == null || current.isEmpty()) {
                    //There is no itemstack in this slot
                    current = stack.clone();
                    amount = 0;
                } else if (stack.getAmount() <= stack.getMaxStackSize()) {
                    //This itemstack is similar to our requested
                    //Only add up on this itemstack
                    amount = current.getAmount();

                    if (amount >= stack.getMaxStackSize()) {
                        //We can't add anything on top of this itemstack
                        continue;
                    }
                } else {
                    continue;
                }

                int newAmount = Math.min(amount + stack.getAmount(), maxStackSize);

                stack.setAmount(stack.getAmount() - (newAmount - amount));
                current.setAmount(newAmount);

                inv.setItem(slot, current);

                if (stack.getAmount() <= 0) {
                    return 0;
                }
            }
        }

        return stack.getAmount();
    }

    private void updateInventoryStatus() {
        ItemStack statusStack = createStatusItem(initiator.hasAccepted() || partner.hasAccepted());

        initiator.getInventory().setItem(CONFIRMATION_INFO_INDEX, statusStack);
        partner.getInventory().setItem(CONFIRMATION_INFO_INDEX, statusStack.clone());

        if (usesMoney()) {
            ItemStack moneyInfo = createMoneyInfo();
            initiator.getInventory().setItem(MONEY_INFO_INDEX, moneyInfo);
            partner.getInventory().setItem(MONEY_INFO_INDEX, moneyInfo.clone());
        }

        if (config.usesXpTrading()) {
            initiator.getInventory().setItem(EXP_INFO_INDEX, createExpInfo(initiator, partner));
            partner.getInventory().setItem(EXP_INFO_INDEX, createExpInfo(partner, initiator));
        }
    }

    private ItemStack createExpInfo(TradePlayer viewer, TradePlayer other) {
        int levelDiff = getLevelDiff(viewer.getPlayer().getLevel(), SetExpFix.getTotalExperience(viewer.getPlayer()),
            other.getExpOffer() - viewer.getExpOffer());

        List<Component> lore = new ArrayList<>();
        lore.add(offerLine(initiator, initiator.getExpOffer() + " XP"));
        lore.add(offerLine(partner, partner.getExpOffer() + " XP"));
        lore.add(Component.empty());
        lore.add(Text.item(i18n.getVarString(Messages.Inventory.LEVEL_INFO)
            .setVariable("level-diff", LEVEL_FORMAT.format(levelDiff))
            .toString()));

        return createItem(config.getXpStatusMaterial(), Text.item(i18n.getString(Messages.Inventory.EXP_INFO_TITLE)), lore);
    }

    private int getLevelDiff(int levelBase, int expBase, int expDiff) {
        int totalExp = expBase + expDiff;
        int level;

        if (expDiff == 0) {
            return 0;
        }

        //Some magic number stuff, based on solving the equations found at
        //https://minecraft.wiki/w/Experience for the level
        if (totalExp < 394) {
            //Level 0 - 16
            level = (int) (Math.sqrt(totalExp + 9) - 3);
        } else if (totalExp < 1628) {
            //Level 17 - 31
            level = (int) (0.632456 * (Math.sqrt(totalExp - 195.975) + 12.8072));
        } else {
            //Level 32+
            level = (int) (0.471405 * (Math.sqrt(totalExp - 752.986) + 38.3016));
        }

        return level - levelBase;
    }

    private enum TradeAction {

        ADD_MONEY,
        ADD_EXP,
        ACCEPT,
        DECLINE,
        MOVE_ITEM_TO_PLAYER_INVENTORY,
        MOVE_ITEM_TO_TRADE_INVENTORY,
        NOTHING

    }

    public interface StateChangedListener {

        void onStateChanged(Trade trade, TradeState newState);

    }

}
