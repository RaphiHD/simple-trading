# SimpleTrading
Paper plugin to allow players to safely trade items, experience and money with one another.

Originally published on [BukkitDev](http://dev.bukkit.org/bukkit-plugins/simple-trading/).

## Requirements

* Paper 26.2 or 26.3
* Java 25
* Optional: [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin for money trading

## Usage

* `/trade <player>` or shift-right-click a player to request a trade
* `/trade accept` / `/trade decline` to answer a request
* `/trade sign [number]` to mark the item in your hand with a configured "untradeable" lore
* `/trade reload` to reload the configuration
* `/trade version` to show the plugin version

All permissions are listed in [plugin.yml](src/main/resources/plugin.yml). They default to operators,
so grant e.g. `simpletrading.trade.initiate`, `simpletrading.trade.accept` and `simpletrading.trade.deny`
to your players with a permissions plugin.

## Compilation

The project is built with Gradle and needs JDK 25:

```
./gradlew build
```

The plugin jar is written to `build/libs/`. To start a local Paper test server with the plugin installed, run:

```
./gradlew runServer
```

## Upgrading from 1.x

Version 2.0 only supports Paper 26.2 and newer. On first start your old `config.yml` is renamed to
`config_old.yml` and a new one is generated. Item names now use modern names such as `lime_dye`
instead of numeric ids and data values like `ink_sack:10`.
