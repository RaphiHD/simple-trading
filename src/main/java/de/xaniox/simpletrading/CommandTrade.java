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

import de.xaniox.simpletrading.config.TradeConfiguration;
import de.xaniox.simpletrading.config.WorldControlManager;
import de.xaniox.simpletrading.i18n.I18N;
import de.xaniox.simpletrading.i18n.I18NManager;
import de.xaniox.simpletrading.i18n.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class CommandTrade implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("accept", "decline", "reload", "sign", "version");

    private final I18N i18n = I18NManager.getGlobal();
    private final SimpleTrading main;

    public CommandTrade(SimpleTrading main) {
        this.main = main;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(i18n.get(Messages.Command.ONLY_PLAYER));
            return;
        }

        TradeFactory factory = main.getFactory();
        WorldControlManager worldControlManager = main.getWorldControlManager();

        if (args.length < 1) {
            player.sendMessage(i18n.getVarString(Messages.Command.USAGE)
                .setVariable("usage", "/trade <player>")
                .toComponent());
            return;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        switch (subcommand) {
            case "accept", "a" -> {
                if (!player.hasPermission(Permissions.TRADE_ACCEPT.getPermission())) {
                    player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
                    return;
                }

                Trade trade = factory.getTrade(player);
                if (trade == null || trade.getPartner().getPlayer() != player) {
                    player.sendMessage(i18n.getVarString(Messages.General.NO_PENDING_REQUESTS)
                        .setVariable("player", player.getName())
                        .toComponent());
                    return;
                }

                Player other = trade.getInitiator().getPlayer();

                if (!isInRange(player, other)) {
                    player.sendMessage(i18n.get(Messages.Command.PARTNER_TOO_FAR_AWAY));
                    return;
                }

                if (!worldControlManager.isAllowed(player.getWorld())) {
                    player.sendMessage(i18n.get(Messages.Command.CANNOT_TRADE_IN_WORLD));
                    return;
                }

                if (!worldControlManager.isAllowed(other.getWorld())) {
                    player.sendMessage(i18n.get(Messages.Command.CANNOT_TRADE_IN_WORLD_PARTNER));
                    return;
                }

                factory.acceptTrade(player);
            }
            case "decline", "d" -> {
                if (!player.hasPermission(Permissions.TRADE_DENY.getPermission())) {
                    player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
                    return;
                }

                Trade trade = factory.getTrade(player);
                if (trade == null || trade.getPartner().getPlayer() != player) {
                    player.sendMessage(i18n.getVarString(Messages.General.NO_PENDING_REQUESTS)
                        .setVariable("player", player.getName())
                        .toComponent());
                    return;
                }

                // declineTrade notifies the initiator
                factory.declineTrade(player);
                player.sendMessage(i18n.getVarString(Messages.General.TRADE_DECLINED)
                    .setVariable("player", trade.getInitiator().getName())
                    .toComponent());
            }
            case "reload" -> {
                if (!player.hasPermission(Permissions.RELOAD.getPermission())) {
                    player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
                    return;
                }

                main.reload();
                player.sendMessage(i18n.get(Messages.Command.CONFIGURATIONS_RELOADED));
            }
            case "sign" -> sign(player, args);
            case "version" -> player.sendMessage(Component.text()
                .append(Component.text("SimpleTrading ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("v" + main.getPluginMeta().getVersion(), NamedTextColor.GREEN))
                .append(Component.text(" by ", NamedTextColor.GRAY))
                .append(Component.text("Matze", NamedTextColor.GREEN))
                .append(Component.text(" (@xaniox/@matzefratze123)", NamedTextColor.GRAY)));
            default -> initiate(player, args[0]);
        }
    }

    private void sign(Player player, String[] args) {
        if (!player.hasPermission(Permissions.SIGN.getPermission())) {
            player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
            return;
        }

        int loreIndex = 0;

        if (args.length > 1) {
            try {
                loreIndex = Integer.parseInt(args[1]) - 1;
            } catch (NumberFormatException nfe) {
                player.sendMessage(i18n.getVarString(Messages.Command.NOT_A_NUMBER)
                    .setVariable("number", args[1])
                    .toComponent());
                return;
            }
        }

        List<String> lores = main.getConfiguration().getItemControlLoreList();
        if (loreIndex < 0 || loreIndex >= lores.size()) {
            player.sendMessage(i18n.getVarString(Messages.Command.NO_LORE_WITH_NUMBER)
                .setVariable("number", String.valueOf(loreIndex + 1))
                .toComponent());
            return;
        }

        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.isEmpty()) {
            player.sendMessage(i18n.get(Messages.Command.NO_ITEM_IN_HAND));
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        List<Component> itemLore = meta.lore();
        itemLore = itemLore == null ? new ArrayList<>() : new ArrayList<>(itemLore);
        itemLore.add(Text.item(lores.get(loreIndex)));
        meta.lore(itemLore);
        stack.setItemMeta(meta);

        player.getInventory().setItemInMainHand(stack);
        player.sendMessage(i18n.get(Messages.Command.LORE_APPLIED));
    }

    private void initiate(Player player, String partnerName) {
        if (!player.hasPermission(Permissions.TRADE_INITIATE.getPermission())) {
            player.sendMessage(i18n.get(Messages.Command.INSUFFICIENT_PERMISSION));
            return;
        }

        Player tradePartner = Bukkit.getPlayer(partnerName);
        if (tradePartner == null || !player.canSee(tradePartner)) {
            player.sendMessage(i18n.getVarString(Messages.Command.PLAYER_NOT_FOUND)
                .setVariable("player", partnerName)
                .toComponent());
            return;
        }

        if (tradePartner == player) {
            player.sendMessage(i18n.get(Messages.Command.NO_SELF_TRADE));
            return;
        }

        if (!main.getConfiguration().allowsCreativeTrading()
                && (player.getGameMode() == GameMode.CREATIVE || tradePartner.getGameMode() == GameMode.CREATIVE)) {
            player.sendMessage(i18n.getVarString(Messages.General.PARTNER_IN_CREATIVE)
                .setVariable("player", tradePartner.getName())
                .toComponent());
            return;
        }

        if (!isInRange(player, tradePartner)) {
            player.sendMessage(i18n.get(Messages.Command.PARTNER_TOO_FAR_AWAY));
            return;
        }

        WorldControlManager worldControlManager = main.getWorldControlManager();
        if (!worldControlManager.isAllowed(player.getWorld())) {
            player.sendMessage(i18n.get(Messages.Command.CANNOT_TRADE_IN_WORLD));
            return;
        }

        if (!worldControlManager.isAllowed(tradePartner.getWorld())) {
            player.sendMessage(i18n.get(Messages.Command.CANNOT_TRADE_IN_WORLD_PARTNER));
            return;
        }

        main.getFactory().initiateTrade(player, tradePartner);
    }

    private boolean isInRange(Player player, Player other) {
        int maxDistance = main.getConfiguration().getMaximumTradeDistance();
        if (maxDistance == TradeConfiguration.NO_MAX_DISTANCE) {
            return true;
        }

        return player.getWorld().equals(other.getWorld())
            && player.getLocation().distanceSquared(other.getLocation()) <= (double) maxDistance * maxDistance;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        CommandSender sender = source.getSender();

        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(current)) {
                    suggestions.add(subcommand);
                }
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == sender || (sender instanceof Player player && !player.canSee(online))) {
                    continue;
                }

                if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                    suggestions.add(online.getName());
                }
            }

            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sign")) {
            List<String> suggestions = new ArrayList<>();
            int lores = main.getConfiguration().getItemControlLoreList().size();
            for (int i = 1; i <= lores; i++) {
                String number = String.valueOf(i);
                if (number.startsWith(current)) {
                    suggestions.add(number);
                }
            }

            return suggestions;
        }

        return List.of();
    }

}
