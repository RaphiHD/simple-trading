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
import de.xaniox.simpletrading.config.TradeConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoveCheckerRunnable implements Runnable {

    private final TradeConfiguration config;
    private final TradeFactory factory;
    private final Map<UUID, World> lastWorldMap;

    public MoveCheckerRunnable(TradeFactory factory, TradeConfiguration config) {
        this.factory = factory;
        this.lastWorldMap = new HashMap<>();
        this.config = config;
    }

    @Override
    public void run() {
        lastWorldMap.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Trade trade = factory.getTrade(player);

            if (trade == null || trade.getState() != TradeState.TRADING) {
                lastWorldMap.remove(player.getUniqueId());
                continue;
            }

            World last = lastWorldMap.putIfAbsent(player.getUniqueId(), player.getWorld());
            if (last == null) {
                continue;
            }

            if (!last.equals(player.getWorld())) {
                factory.stopTrade(trade, StopCause.LEFT_WORLD, player);
                continue;
            }

            final int maxDistance = config.getMaximumTradeDistance();
            if (maxDistance == TradeConfiguration.NO_MAX_DISTANCE) {
                continue;
            }

            Location initiatorLocation = trade.getInitiator().getPlayer().getLocation();
            Location partnerLocation = trade.getPartner().getPlayer().getLocation();

            if (!initiatorLocation.getWorld().equals(partnerLocation.getWorld())
                    || initiatorLocation.distanceSquared(partnerLocation) > (double) maxDistance * maxDistance) {
                factory.stopTrade(trade, StopCause.MOVE, player);
            }
        }
    }

}
