# CityRoleplay

Production Minecraft city-roleplay plugin for **Paper-compatible 1.21.11 servers (Leaf)** on **Java 21**.

CityRoleplay provides police duty zones, a police dashboard and tool shop, criminal records, KOTH-style store robberies, arrests and prison sentences, a player selling GUI, an internal/Vault economy, and a full in-game administration panel. All gameplay data is stored in SQLite; all player-facing text and gameplay settings are configurable.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | 1.21.11 |
| Server | Leaf, Paper, or another Paper-compatible 1.21.11 server |
| Java | 21 |
| Build | Maven 3.9+ |
| Permissions | LuckPerms (optional; a permission fallback is included) |
| Economy | Vault + EssentialsX (optional; an internal economy is included) |
| Database | SQLite (bundled JDBC driver, no server required) |

## Build

```bash
cd city-roleplay
mvn clean package
```

Output: `city-roleplay/target/CityRoleplay-1.0.0.jar`

Automated tests run during `package`:

```bash
mvn test
```

## Install (short version)

1. Place the JAR in `plugins/`.
2. Start the server once to generate `plugins/CityRoleplay/`.
3. Install/configure LuckPerms and Vault + EssentialsX if you want those integrations.
4. Create a prison destination reachable by the configured command; the default is `warp prison %player%`.
5. Grant `city.police` to your police group and the desired `city.tools.*` permissions.
6. Run `/cityadmin` and create duty and robbery zones with the selection wand.
7. Run `/cityadmin reload` after manual YAML edits.

Full instructions: [docs/INSTALLATION.md](docs/INSTALLATION.md).

## Documentation

- [Installation](docs/INSTALLATION.md)
- [Commands](docs/COMMANDS.md)
- [Permissions](docs/PERMISSIONS.md)
- [Administration](docs/ADMINISTRATION.md)
- [Testing](docs/TESTING.md)
- [Feature checklist](docs/FEATURE-CHECKLIST.md)

## Systems

| Module | Responsibility |
|---|---|
| Police | Police membership, dashboard, statistics, duty zones, grave registration |
| Criminal | Criminal flag, wanted expiry, witnessed robbery/kill, private notifications |
| Prison | Sentences, console teleport command, release, reconnect enforcement |
| Robbery | Cuboid capture locations, boss bar, cooldown, interruption, AFK checks |
| AFKZone | Multiple duty cuboids, per-zone rewards/intervals, overlap resolution |
| PoliceTools | Dynamic tool registry, PDC-serialised items, five real behaviors |
| Economy | Vault bridge with internal fallback, transactional balance changes |
| Shop | Police tool shop, limits, confirmation, delivery-failure refund |
| PlayerProfile | Cached profile access and async SQLite persistence |
| Database | Schema, profiles, transactions, robberies, arrests, graves, prison records |
| GUIManager | Inventory holder routing, click cancellation, navigation |
| CommandManager | Command registration without clobbering EssentialsX `/eco` |
| IntegrationManager | Loader-safe LuckPerms and Vault bridges |
| ConfigurationManager | Ordered YAML loading, saving and safe reload |

## Configuration files

Generated under `plugins/CityRoleplay/`:

```
config.yml          general settings, timezone, save interval
messages.yml        every player-facing message
police.yml          police group, rewards, fines, duty defaults, arrest rules
criminals.yml       witness radius, wanted duration, privacy
robbery.yml         capture defaults and generated robbery zones
prison.yml          sentence lengths, jail/release commands
economy.yml         provider, precision, starting balance, limits
shop.yml            shop presentation and confirmation
tools.yml           dynamic police tool registry
afk-zones.yml       generated duty zones
sell.yml            sell prices, whitelist, blacklist
integrations.yml    soft-integration options
database.db         SQLite database (created automatically)
```

## Safety properties

- Purchased tools carry a PDC tool id, serial and owner; validation checks all three.
- Tool serials are persisted, so copied/duplicated serials are rejected after restart.
- Soulbound purchased tools cannot be dropped.
- GUI clicks are cancelled except for the intentional sell-menu slots.
- Robbery completion and cooldown are committed in one SQLite transaction.
- A grave can be rewarded once, enforced by a unique database row.
- `/sell` returns items on close and validates before removing or paying.
- Prison commands run from console, with only a validated player name substituted for `%player%`.
- Criminal status is private by default; it is not broadcast in chat, TAB, or scoreboards.
- LuckPerms and Vault are loaded reflectively so their absence cannot break plugin startup.

## Verified environment

The delivered build was tested on **Paper 1.21.11 build 132**, Java 21, offline-mode test server, with no LuckPerms or Vault installed. That exercises the permission fallback and internal economy. See [docs/TESTING.md](docs/TESTING.md) for the exact results and the integrations that could not be exercised.

## License / ownership

Delivered as a custom private project for the server owner. No third-party premium code is included.
