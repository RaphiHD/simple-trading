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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Converts the legacy {@code &}-formatted strings used in the locale and
 * configuration files into Adventure components.
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    /** Parses a legacy {@code &}-formatted string into a component, e.g. for chat messages. */
    public static Component of(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /** Parses a string for use as an item name or lore line, which are not italic unless requested. */
    public static Component item(String legacy) {
        return of(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Parses every line for use as item lore. */
    public static List<Component> lore(Collection<String> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(item(line));
        }
        return lore;
    }

    /** Parses a string containing {@code \n} separated lines for use as item lore. */
    public static List<Component> lore(String lines) {
        return lore(List.of(lines.split("\n")));
    }

    /** Returns the text of a component without any formatting. */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

}
