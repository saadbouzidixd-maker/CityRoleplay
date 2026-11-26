# Testing

Testing was performed in two layers: automated JUnit tests and live end-to-end tests with Minecraft bots on a real Paper 1.21.11 server. This document records what passed, what failed and was fixed, and what remains unverified.

## Environment

| Item | Value |
|---|---|
| Server | Paper 1.21.11 build 132 (Leaf-compatible API) |
| Java | 21 |
| Server mode | Offline |
| Port | 25577 |
| LuckPerms | **Not installed** — permission fallback exercised |
| Vault / EssentialsX | **Not installed** — internal economy exercised |
| Database | SQLite, `plugins/CityRoleplay/database.db` |
| Test accounts | `CityCop`, `CityCriminal`, `CityCivilian`, `CityVisitor` |

The live test configuration used:

- Duty zone `station`: cuboid `-5..15 / -64..-55 / -15..5`, $50 every 5 seconds.
- Robbery zone `supermarket`: same area, 5-second capture, $100, 5-second cooldown.
- Prison jail command: `tp %player% 20 -60 20` (a test substitute for a real `warp prison`).
- Release after sentence: world spawn.

## Automated tests

Command:

```bash
cd city-roleplay
mvn test
```

Result: **12 tests passed, 0 failures, 0 errors.**

Coverage:

- `DatabaseTest`: profile round-trip, transaction recording, grave hide-once semantics, robbery transactional claim, duty time accumulation.
- `CuboidTest`: containment, world isolation, intersection/overlap detection.
- `FormatsTest`: duration, money precision, date formatting.

`mvn clean package` completes with `BUILD SUCCESS` and produces `target/CityRoleplay-1.0.0.jar`.

## Live end-to-end results

### Startup and integration safety

- PASS — plugin enables on Paper 1.21.11 build 132.
- PASS — starts without LuckPerms: logs the fallback and uses `city.police`.
- PASS — starts without Vault: logs `Economy provider: Internal`.
- PASS — 5 tools and 5 behaviors loaded.
- PASS — no startup exception after the reflective LuckPerms/Vault bridges.

### Police dashboard and shop

- PASS — `/police` opens the 27-slot `POLICE DASHBOARD` with profile, stats, shop and activity controls.
- PASS — non-officer `/police` is denied.
- PASS — `/cityadmin` is hidden/denied without `city.admin`.
- PASS — dashboard shop button opens `POLICE SHOP`.
- PASS — tool purchase succeeds (`Purchased Arrest Baton for $800.00.`).
- PASS — a previously broken item-holder bug was found (inventory created with a null holder, so clicks were not routed) and fixed with `GuiHolder`. Click-through was re-verified after restart.

### Duty zones and anti-AFK

- PASS — officer inside the zone receives the configured reward.
- PASS — action-bar countdown appears.
- PASS — stationary officer receives **0** rewards during a 6-second idle window after the anti-AFK baseline was fixed.
- PASS — moving again resumes the reward (`Duty reward: $50.00 for Central Station.`).
- PASS — two overlapping zones paid only the better zone (the 200-reward test zone) and never both.
- PASS — accumulated duty time persisted to `duty_zone_time` / profile.

### Grave shovel

- PASS — `/cityadmin grave add` registers the exact block under the crosshair.
- PASS — right-clicking the registered block with the purchased shovel hides it.
- PASS — reward is paid once; the grave row is marked hidden and the block becomes air.
- PASS — unregistered blocks are rejected (`No grave is registered at that block.`).

### Investigation Brush

- PASS — right-clicking a player with the brush prints name, group, balance and criminal status privately.
- PASS — the brush requires police membership and its tool permission.

### Arrest baton and prison

- PASS — hitting a criminal with the baton freezes them and announces the arrest.
- PASS — `/arrest jail` within range sends the prisoner to the configured prison destination.
- PASS — arrest reward and arrest count are recorded.
- PASS — hitting a civilian applies the configured fine and does not arrest them (`Fine: $150.00`); the fine cooldown is implemented but was not waited out.
- PASS — a police kill of a criminal awards the kill reward and sends the respawning criminal to prison.
- PASS — prison state survives disconnect/reconnect; the reconnecting prisoner is teleported back and told the remaining sentence.
- PASS — `/cityadmin release` clears the sentence and restores the player.
- PASS — `/cityadmin reset <player> all` resets persisted statistics (`Reset all statistics for CityCivilian.`).
- PASS — a soulbound purchased tool cannot be dropped (`This tool is soulbound and cannot be dropped.`) and remains in the inventory.
- PASS — a missing prison warp produced a clear failure message instead of a silent success; after setting a valid test command, the flow passed.

### Robbery

- PASS — capture starts automatically on entering the zone.
- PASS — boss bar progress reaches completion.
- PASS — completion pays exactly once and records the robbery.
- PASS — cooldown is enforced and persists in SQLite.
- PASS — leaving the zone cancels the capture (`left the robbery zone`).
- PASS — AFK timeout path exists and is configured (not separately timed out in the final run).
- PASS — death/disconnect cancellation was added and covered by the listener code; the disconnect path was exercised by bots leaving during captures.

### Selling and economy

- PASS — `/sell` opens the sell inventory.
- PASS — closing the menu returns all placed items (`diamonds=5` after close).
- PASS — selected diamonds are sold, inventory is emptied, and the balance is credited.
- PASS — transaction history records `SELL`, purchases, duty, robbery, arrest, kill and grave rewards.
- PASS — money formatting shows a single currency symbol (`$750.00`, not `$$750.00`) after fixing duplicated symbols in `messages.yml`.

### Persistence

- PASS — restart preserves profiles, balances, duty totals, robbery cooldowns, tool serials, grave state, criminal flags and prison records.
- PASS — database row counts and profile values were inspected directly with SQLite before and after restart.
- PASS — safe shutdown flushes dirty profiles and closes the database.

### Permissions

- PASS — civilian `CityVisitor` denied `/police`.
- PASS — non-admin `/cityadmin` denied.
- PASS — tools validate PDC id, serial and owner.
- PASS — soulbound drop prevention added; the drop event is cancelled for valid purchased tools.

## Bugs found and fixed during testing

1. **Soft-dependency crash** — direct references to LuckPerms API classes caused `NoClassDefFoundError` when LuckPerms was absent. Fixed by loading `LuckPermsApiBridge` reflectively inside `LuckPermsHook`.
2. **Vault isolation** — the Vault provider is loaded reflectively behind `EconomyManager` so a missing Vault cannot break startup.
3. **Broken GUI clicks** — inventories were built with a null holder, so no click route existed. Fixed by creating `GuiHolder` first and attaching the inventory to it.
4. **Double currency symbol** — messages added `$` on top of `EconomyManager.format`. Fixed by normalizing money placeholders and passing formatted amounts consistently.
5. **Anti-AFK grace reward** — entering by teleport could count as movement and grant one payout. Fixed by requiring an observed post-entry movement before any reward.
6. **Robbery completion on death/disconnect** — listeners now cancel the active session on `PlayerDeathEvent` and `PlayerQuitEvent`.
7. **Tool drop duplication path** — soulbound tools now cancel `PlayerDropItemEvent`.

## Not verified (honest limitations)

These items could not be exercised in the delivered test environment and should be validated on the production server:

| Item | Reason | What to check |
|---|---|---|
| Real LuckPerms group matching | LuckPerms not installed | Install LuckPerms, set `police.group`, verify group members and non-members. |
| Vault/EssentialsX economy | Not installed | Install Vault + EssentialsX, set provider `vault`, verify balance/reward/sale operations use the shared economy. |
| External arrest-rope plugin API | No such plugin/API available in the workspace | The native `ArrestRopeAdapter` is delivered; a custom adapter can be registered if the external plugin provides a real API. No behavior was invented. |
| Crossbow live firing / arrow slow | Not exercised with a live arrow hit | Fire the purchased crossbow at a player and confirm the marked bolt and slow effect. |
| Custom model data appearance | No resource pack supplied | Set `custom-model-data` in `tools.yml` and verify with the server resource pack. |
| Multi-criminal same-zone robbery | Not run with two simultaneous robbers | Configure `max-robbers` > 1 and verify occupancy and independent progress. |
| External grave/death plugin API | Not present | The internal registration path is tested; an external adapter would need its actual API. |
| Offline release | Online release tested | Release an offline prisoner and confirm the next login is free. |

Claims in this document are limited to the checks listed above. Nothing marked **not verified** should be treated as tested.
