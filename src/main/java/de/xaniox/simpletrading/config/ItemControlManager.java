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
package de.xaniox.simpletrading.config;

import de.xaniox.simpletrading.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ItemControlManager extends ControlManager<ItemStack> {

    private Set<Material> items;
    private List<String> lores;

    public ItemControlManager(TradeConfiguration config) {
        super(config.getItemControlMode());

        updateValues(config);
    }

    @Override
    public void updateValues(TradeConfiguration config) {
        setMode(config.getItemControlMode());
        this.items = config.getItemControlList();

        // Compare the plain text only, so color codes don't matter
        List<String> plainLores = new ArrayList<>();
        for (String lore : config.getItemControlLoreList()) {
            plainLores.add(Text.plain(Text.of(lore)));
        }
        this.lores = plainLores;
    }

    @Override
    protected boolean isAllowedBlacklist(ItemStack stack) {
        if (items.contains(stack.getType())) {
            return false;
        }

        if (!stack.hasItemMeta()) {
            return true;
        }

        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return true;
        }

        for (Component line : lore) {
            if (lores.contains(Text.plain(line))) {
                return false;
            }
        }

        return true;
    }

}
