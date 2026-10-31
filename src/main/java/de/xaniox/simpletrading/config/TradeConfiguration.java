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

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class TradeConfiguration {

    private static final String PLAYERNAME_PLACEHOLDER = "@p";
    /** Matches a trailing legacy data value like {@code :10} in {@code ink_sack:10}. */
    private static final Pattern LEGACY_DATA_SUFFIX = Pattern.compile(":\\d+$");
    public static final int NO_MAX_DISTANCE = -1;
    public static final int CURRENT_CONFIG_VERSION = 8;
    public static final String DESTINATION_FILE_NAME = "config.yml";

    private final Logger logger;

    private Material acceptMaterial;
    private Material declineMaterial;
    private Material seperatorMaterial;
    private Material moneyStatusMaterial;
    private Material moneyAddRemoveMaterial;
    private Material xpStatusMaterial;
    private Material xpAddRemoveMaterial;
    private Locale locale;
    private String inventoryName;
    private int moneyValue1;
    private int moneyValue2;
    private int moneyValue3;
    private int expValue1;
    private int expValue2;
    private int expValue3;
    private int maximumTradeDistance;
    private boolean allowCreativeTrading;
    private int timeout;
    private boolean useXpTrading;
    private boolean useMoneyTrading;
    private boolean useShiftTrading;
    private int maxMoneyTrading;
    private boolean abortOnDecline;
    private ControlMode itemControlMode;
    private Set<Material> itemControlItems;
    private List<String> itemControlLores;
    private ControlMode worldControlMode;
    private List<String> worldControlList;

    public TradeConfiguration(Configuration config, Logger logger) {
        this.logger = logger;
        loadByConfiguration(config);
    }

    private static ConfigurationSection section(Configuration config, String name) {
        ConfigurationSection section = config.getConfigurationSection(name);
        return section != null ? section : new MemoryConfiguration();
    }

    public void loadByConfiguration(Configuration config) {
        ConfigurationSection blockSection = section(config, "blocks");
        acceptMaterial = readMaterial(blockSection, "accept", Material.LIME_DYE);
        declineMaterial = readMaterial(blockSection, "decline", Material.RED_DYE);
        seperatorMaterial = readMaterial(blockSection, "seperator", Material.BARRIER);
        moneyStatusMaterial = readMaterial(blockSection, "money-status", Material.GOLD_NUGGET);
        moneyAddRemoveMaterial = readMaterial(blockSection, "money-add-remove", Material.GOLD_NUGGET);
        xpStatusMaterial = readMaterial(blockSection, "xp-status", Material.EXPERIENCE_BOTTLE);
        xpAddRemoveMaterial = readMaterial(blockSection, "xp-add-remove", Material.EXPERIENCE_BOTTLE);

        ConfigurationSection localizationSection = section(config, "localization");
        locale = parseLocale(localizationSection.getString("locale", "en_US"));

        ConfigurationSection inventorySection = section(config, "inventory");
        inventoryName = inventorySection.getString("name", "SimpleTrading - @p");
        moneyValue1 = inventorySection.getInt("money-value-1", 50);
        moneyValue2 = inventorySection.getInt("money-value-2", 100);
        moneyValue3 = inventorySection.getInt("money-value-3", 500);
        expValue1 = inventorySection.getInt("exp-value-1", 5);
        expValue2 = inventorySection.getInt("exp-value-2", 50);
        expValue3 = inventorySection.getInt("exp-value-3", 100);

        ConfigurationSection globalSection = section(config, "global");
        maximumTradeDistance = globalSection.getInt("max-distance", 15);
        allowCreativeTrading = globalSection.getBoolean("creative-trading", true);
        timeout = globalSection.getInt("timeout", 60);
        useXpTrading = globalSection.getBoolean("use-xp-trading", true);
        useMoneyTrading = globalSection.getBoolean("use-money-trading", true);
        useShiftTrading = globalSection.getBoolean("use-shift-trading", true);
        maxMoneyTrading = globalSection.getInt("max-money-trading", -1);
        abortOnDecline = globalSection.getBoolean("abort-on-decline", false);

        ConfigurationSection itemControlSection = section(config, "item-control");
        itemControlMode = ControlMode.getMode(itemControlSection.getString("control-mode"), ControlMode.BLACKLIST);
        itemControlItems = EnumSet.noneOf(Material.class);

        for (String controlItemString : itemControlSection.getStringList("item-list")) {
            Material material = parseMaterial(controlItemString);
            if (material == null) {
                logger.warning("Unknown item \"" + controlItemString + "\" in item-control.item-list, ignoring it");
                continue;
            }

            itemControlItems.add(material);
        }

        itemControlLores = itemControlSection.getStringList("item-lore");

        ConfigurationSection worldControlSection = section(config, "world-control");
        worldControlMode = ControlMode.getMode(worldControlSection.getString("control-mode"), ControlMode.BLACKLIST);
        worldControlList = worldControlSection.getStringList("world-list");
    }

    private Material readMaterial(ConfigurationSection section, String key, Material def) {
        String value = section.getString(key);
        if (value == null) {
            return def;
        }

        Material material = parseMaterial(value);
        if (material == null) {
            logger.warning("Unknown item \"" + value + "\" for blocks." + key + ", using " + def.name().toLowerCase(Locale.ROOT) + " instead");
            return def;
        }

        return material;
    }

    /**
     * Parses a modern item name such as {@code lime_dye} or {@code minecraft:lime_dye}.
     * A legacy data suffix like {@code :10} is ignored.
     *
     * @return the item, or null if the name is unknown or not an item
     */
    static Material parseMaterial(String value) {
        String name = LEGACY_DATA_SUFFIX.matcher(value.trim()).replaceFirst("");
        Material material = Material.matchMaterial(name);

        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }

        return material;
    }

    private static Locale parseLocale(String localeString) {
        String[] parts = localeString.split("_");

        String language = parts[0];
        String country = parts.length > 1 ? parts[1] : "";
        String variant = parts.length > 2 ? parts[2] : "";

        return Locale.of(language, country, variant);
    }

    public Material getAcceptMaterial() {
        return acceptMaterial;
    }

    public Material getDeclineMaterial() {
        return declineMaterial;
    }

    public Material getSeperatorMaterial() {
        return seperatorMaterial;
    }

    public Material getMoneyStatusMaterial() {
        return moneyStatusMaterial;
    }

    public Material getMoneyAddRemoveMaterial() {
        return moneyAddRemoveMaterial;
    }

    public Material getXpStatusMaterial() {
        return xpStatusMaterial;
    }

    public Material getXpAddRemoveMaterial() {
        return xpAddRemoveMaterial;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getInventoryName(String nameReplacement) {
        return inventoryName.replace(PLAYERNAME_PLACEHOLDER, nameReplacement);
    }

    public int getMoneyValue1() {
        return moneyValue1;
    }

    public int getMoneyValue2() {
        return moneyValue2;
    }

    public int getMoneyValue3() {
        return moneyValue3;
    }

    public int getExpValue1() {
        return expValue1;
    }

    public int getExpValue2() {
        return expValue2;
    }

    public int getExpValue3() {
        return expValue3;
    }

    public int getMaximumTradeDistance() {
        return maximumTradeDistance;
    }

    public boolean allowsCreativeTrading() {
        return allowCreativeTrading;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean usesXpTrading() {
        return useXpTrading;
    }

    public boolean usesMoneyTrading() {
        return useMoneyTrading;
    }

    public boolean usesShiftTrading() {
        return useShiftTrading;
    }

    public int getMaxMoneyTrading() {
        return maxMoneyTrading;
    }

    public boolean isAbortOnDecline() {
        return abortOnDecline;
    }

    public ControlMode getItemControlMode() {
        return itemControlMode;
    }

    public Set<Material> getItemControlList() {
        return itemControlItems;
    }

    public List<String> getItemControlLoreList() {
        return itemControlLores;
    }

    public ControlMode getWorldControlMode() {
        return worldControlMode;
    }

    public List<String> getWorldControlList() {
        return worldControlList;
    }

}
