# Installation

## 1. Requirements

- Leaf or Paper 1.21.11
- Java 21
- Maven 3.9+ only when building from source

Optional:

- LuckPerms 5.x for group-based police detection
- Vault + EssentialsX for the shared server economy
- A prison warp/teleport destination

## 2. Build or obtain the JAR

From the project folder:

```bash
cd city-roleplay
mvn clean package
```

Copy `target/CityRoleplay-1.0.0.jar` into the server's `plugins/` folder.

## 3. First start

Start the server. CityRoleplay creates:

```
plugins/CityRoleplay/
  config.yml
  messages.yml
  police.yml
  criminals.yml
  robbery.yml
  prison.yml
  economy.yml
  shop.yml
  tools.yml
  afk-zones.yml
  sell.yml
  integrations.yml
  database.db
```

The console should report:

```
[CityRoleplay] Loaded 5 tools and 5 behaviors.
[CityRoleplay] CityRoleplay enabled: ... profiles, ... duty zones, ... robbery zones, 5 tools.
```

If the database cannot be opened, the plugin disables itself rather than running without persistence.

## 4. LuckPerms setup

The default police group is `POLICE`, configured in `police.yml`:

```yaml
police:
  group: POLICE
  permission-fallback: city.police
```

Create the group and grant the base permission:

```
/lp creategroup police
/lp group police permission set city.police true
/lp group police permission set city.tools.grave true
/lp group police permission set city.tools.brush true
/lp group police permission set city.tools.rope true
/lp group police permission set city.tools.crossbow true
/lp group police permission set city.tools.baton true
/lp group police permission set city.arrest true
/lp user <name> parent add police
```

The configured group name is matched case-insensitively. The plugin does not assign or remove unrelated permissions. If LuckPerms is unavailable, `city.police` is used directly, so operator accounts and permission plugins still work.

## 5. Economy setup

`economy.yml` accepts:

```yaml
economy:
  provider: auto     # auto, vault, internal
  precision: 2
  starting-balance: 1000.0
```

- `auto`: use Vault when a Vault economy provider is registered, otherwise the internal SQLite economy.
- `vault`: require Vault; if it is missing, startup logs the problem and the internal provider is used.
- `internal`: never use Vault.

With EssentialsX installed, keep `integrations.yml`:

```yaml
essentials:
  respect-existing-eco-command: true
```

This prevents CityRoleplay from registering `/eco` when EssentialsX already owns it. When no plugin owns `/eco`, CityRoleplay registers `/eco give|take|set <player> <amount>` for `city.admin` holders.

## 6. Prison destination

The default sentence action is:

```yaml
prison:
  jail-command: "warp prison %player%"
```

Create the destination on your server (for example with EssentialsX warps) before using arrests. Only `%player%` is substituted; it is replaced with a validated online player name, and the command runs from console. There is no arbitrary placeholder expansion.

For a test server without a warp plugin, a temporary value is acceptable:

```yaml
jail-command: "tp %player% 20 -60 20"
```

Release behavior:

- If `release-command` is set, it runs from console with `%player%`.
- Otherwise the player is teleported to the world spawn, or to the configured `release-world` spawn.

## 7. Create gameplay areas

1. In game as an administrator, run `/cityadmin wand`.
2. Left-click the first corner and right-click the second corner.
3. Open `/cityadmin` -> **Duty Zones** -> **Create** for paid police zones.
4. Open `/cityadmin` -> **Robberies** -> **Create** for robbery locations.
5. Follow the chat prompts for id, display name, reward, interval/duration and cooldown.

Defaults for robbery zones are a **300-second capture** and configurable reward/cooldown. Duty-zone reward and interval are entered during creation.

## 8. Reload and restart behavior

`/cityadmin reload` reloads configuration files, messages, zones, tools, integrations and economy provider state. It does not delete profiles or transaction history.

Player profiles, criminal status, prison time, grave operations, tool serials, robbery cooldowns and duty totals are stored in SQLite and survive restarts. Back up `database.db` (and the `-wal`/`-shm` files while the server is running or stopped cleanly) as part of normal server backups.

## 9. Updating

1. Stop the server or use a maintenance window.
2. Back up `plugins/CityRoleplay/`.
3. Replace the JAR.
4. Start the server.
5. Review new configuration keys; existing files are not overwritten.

Do not delete `database.db` during an update.

## 10. Uninstalling

Remove the JAR and the `plugins/CityRoleplay/` folder. Keeping the folder preserves player data if you reinstall later.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `LuckPerms is not installed` | Expected when LuckPerms is absent; `city.police` fallback is active. |
| `Economy provider: Internal` | Vault or a Vault economy provider is absent; install Vault + EssentialsX or keep the internal economy. |
| `The prison warp command failed` | The configured `jail-command` is missing or its destination does not exist. Create `warp prison` or change the command. |
| Police command says not an officer | Grant `city.police`, or add the player to the configured LuckPerms group. |
| No duty reward | The officer must be police, inside a zone, and must move if anti-AFK is enabled. Check the action-bar countdown. |
| Robbery gives cooldown | Cooldowns persist across restarts. Check `robbery.yml` / the zone settings and the database `robbery_cooldowns` table. |
| Shop purchase fails | Check the player's permission, balance, purchase limit, and available inventory space. |
