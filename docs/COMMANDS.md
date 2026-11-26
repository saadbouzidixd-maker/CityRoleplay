# Commands

All commands are registered by CityRoleplay except `/eco`, which is registered only when no other plugin owns it.

## Player and police commands

| Command | Permission | Description |
|---|---|---|
| `/police` | `city.police` | Opens the police dashboard. |
| `/police shop` | `city.police` + tool permission | Opens the police tool shop. |
| `/police stats` | `city.police` | Opens the statistics view. |
| `/police activity` | `city.police` | Opens recent activity/transaction history. |
| `/arrest <jail\|release\|escort> [player]` | `city.police` / `city.arrest` | Completes an arrest, releases, or toggles escort. Without a name, uses the looked-at or nearest arrested player. |
| `/sell` | `city.sell` | Opens the selling GUI. |
| `/eco give <player> <amount>` | `city.admin` | Fallback economy command, only if `/eco` is not already registered. |
| `/eco take <player> <amount>` | `city.admin` | Fallback economy removal. |
| `/eco set <player> <amount>` | `city.admin` | Fallback balance set. |

### Police dashboard

The dashboard shows the officer's profile, kills, arrests, duty time, police earnings, joining date, balance, shop, statistics and activity buttons. The shop enforces prices, permissions, purchase limits and inventory space, then asks for confirmation.

### Arrest workflow

1. Hit a criminal with the purchased **Arrest Baton**.
2. The criminal freezes and is notified.
3. Use `/arrest escort <player>` or right-click with the Arrest Rope to tow them.
4. Stay within the configured jail distance and run `/arrest jail <player>`.
5. The configured console jail command runs, the sentence is recorded, and the officer is paid.

A police kill of a criminal uses the separate death path: reward, death record, prison schedule after respawn.

## Administration

`/cityadmin` requires `city.admin`. Its alias is `/ca`.

| Command | Description |
|---|---|
| `/cityadmin` | Opens the main admin GUI. |
| `/cityadmin reload` | Reloads configurations and manager state. |
| `/cityadmin zones` | Duty-zone management GUI. |
| `/cityadmin robbery` | Robbery-location management GUI. |
| `/cityadmin tools` | Dynamic tool registry GUI. |
| `/cityadmin records` | Player records (police/criminal/all filters). |
| `/cityadmin transactions` | Recent transaction log. |
| `/cityadmin prison` | Current prisoners; click to release. |
| `/cityadmin wand` | Gives the corner-selection wand. |
| `/cityadmin grave add\|remove` | Registers/removes the block being looked at as a grave. |
| `/cityadmin release <player>` | Releases an online or offline prisoner. |
| `/cityadmin police add\|remove <player>` | Adds/removes the player from the configured LuckPerms police group. |
| `/cityadmin criminal set\|clear <player>` | Sets or clears criminal status. |
| `/cityadmin reset <player> <police\|criminal\|all>` | Resets persisted statistics. |
| `/cityadmin tool give <player> <tool>` | Issues a registered tool without charging. |
| `/cityadmin eco <give\|take\|set> <player> <amount>` | Administrative economy operations. |
| `/cityadmin help` | Command summary. |

### Zone creation

1. `/cityadmin wand`
2. Left-click corner 1, right-click corner 2.
3. `/cityadmin zones` or `/cityadmin robbery`
4. Click the green **Create** item.
5. Enter id, display name, and the requested numeric values in chat.

Zone geometry edits are done by deleting and recreating the zone. Reward, interval, enabled state, and (for robberies) duration/cooldown are editable from the GUI. Per-zone permissions and LuckPerms groups are configured in the YAML files.

## Tab completion

- `/police`: `shop`, `stats`, `activity`
- `/arrest`: `jail`, `release`, `escort`, then online player names
- `/cityadmin`: all top-level subcommands, including `reset`
- `/eco`: `give`, `take`, `set`, then online names

## Command conflicts

- `/eco` is skipped when another plugin already registered it and `integrations.essentials.respect-existing-eco-command` is `true`.
- `/sell` and `/police` are dedicated plugin commands. If another plugin already uses those exact command names, Bukkit will report the conflict at startup; rename/remove the conflicting command or the plugin owning it.
- `/arrest` is also a dedicated command. It does not override a command owned by another plugin unless Bukkit allows the override; check startup logs on a heavily modified server.
