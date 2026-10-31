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
import de.xaniox.simpletrading.i18n.I18NBuilder;
import de.xaniox.simpletrading.i18n.I18NManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

public class SimpleTrading extends JavaPlugin {

    private static final String I18N_CLASSPATH_FOLDER = "i18n/";
    private static final String VAULT_PLUGIN_NAME = "Vault";

    private TradeConfiguration config;
    private I18NManager i18nManager;
    private TradeFactory factory;
    private BukkitTask movementTask;
    private ItemControlManager itemControlManager;
    private WorldControlManager worldControlManager;

    private Economy econ;
    private boolean isDisabling;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        checkConfigVersion();

        File i18nFolder = new File(getDataFolder(), "i18n");
        i18nFolder.mkdirs();

        config = new TradeConfiguration(getConfig(), getLogger());
        I18NManager.setGlobalBuilder(I18NBuilder.builder()
            .setFileSystemFolder(i18nFolder)
            .setClasspathFolder(I18N_CLASSPATH_FOLDER)
            .setLoadingMode(I18N.LoadingMode.FILE_SYSTEM)
            .setLocale(config.getLocale())
            .setClassLoader(getClassLoader())
            .setLogger(getLogger()));
        i18nManager = new I18NManager();

        if (getEconomy() == null) {
            getLogger().info("Vault or an economy plugin was not found, money trading is disabled.");
        }

        itemControlManager = new ItemControlManager(config);
        worldControlManager = new WorldControlManager(config);

        factory = new TradeFactory(this, config, itemControlManager);

        registerCommand("trade", "Basic trade command", List.of(), new CommandTrade(this));
        movementTask = getServer().getScheduler().runTaskTimer(this, new MoveCheckerRunnable(factory, config), 20L, 30L);

        getLogger().info("SimpleTrading v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        isDisabling = true;
        HandlerList.unregisterAll(this);

        if (movementTask != null) {
            movementTask.cancel();
        }

        if (factory != null) {
            factory.stopAllTrades(StopCause.SERVER_SHUTDOWN);
        }
    }

    private void checkConfigVersion() {
        Path dataFolder = getDataFolder().toPath();
        Path configSource = dataFolder.resolve(TradeConfiguration.DESTINATION_FILE_NAME);
        YamlConfiguration checkConfig = YamlConfiguration.loadConfiguration(configSource.toFile());

        if (checkConfig.getInt("config-version", 0) >= TradeConfiguration.CURRENT_CONFIG_VERSION) {
            return;
        }

        Path configTarget = dataFolder.resolve("config_old.yml");

        try {
            Files.move(configSource, configTarget, StandardCopyOption.REPLACE_EXISTING);
            saveResource(TradeConfiguration.DESTINATION_FILE_NAME, true);
            reloadConfig();

            getLogger().warning("Due to a SimpleTrading update your old configuration has been renamed");
            getLogger().warning("to config_old.yml and a new one has been generated. Make sure to");
            getLogger().warning("apply your old changes to the new config!");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not create updated configuration due to an IOException", e);
        }
    }

    public void reload() {
        reloadConfig();
        config.loadByConfiguration(getConfig());
        i18nManager.reloadAll(config.getLocale());

        itemControlManager.updateValues(config);
        worldControlManager.updateValues(config);
    }

    public static void copyResource(URL resourceUrl, File destination) throws IOException {
        URLConnection connection = resourceUrl.openConnection();
        connection.setUseCaches(false);

        File parent = destination.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (InputStream inStream = connection.getInputStream()) {
            Files.copy(inStream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean usesVault() {
        return getEconomy() != null;
    }

    /**
     * Returns the Vault economy, looking it up lazily so that economy plugins
     * which register their provider after SimpleTrading has been enabled are still found.
     */
    public Economy getEconomy() {
        if (econ == null && getServer().getPluginManager().isPluginEnabled(VAULT_PLUGIN_NAME)) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
        }

        return econ;
    }

    public boolean isDisabling() {
        return isDisabling;
    }

    public TradeFactory getFactory() {
        return factory;
    }

    public TradeConfiguration getConfiguration() {
        return config;
    }

    public WorldControlManager getWorldControlManager() {
        return worldControlManager;
    }
}
