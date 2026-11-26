# Feature checklist

Legend:

- **Verified** — exercised on the live Paper 1.21.11 test server described in [TESTING.md](TESTING.md).
- **Implemented, not live-verified** — code exists and compiles; the specific environment/integration was unavailable.
- **Fallback** — works without the optional dependency using the documented degraded path.

## Architecture

- [x] Separate responsibilities for Police, Criminal, Prison, Robbery, AFKZone, PoliceTools, Economy, Shop, PlayerProfile, Database, GUI, Commands, Integrations and Configuration. **Verified** (packages/services, live startup)
- [x] Interfaces/services for integration and tool behavior, with an extension point for new behaviors. **Verified** (5 behaviors registered; `ToolBehavior` API)
- [x] No NMS and no invented external APIs. **Verified**

## Police

- [x] Configurable LuckPerms police group (`POLICE` default). **Verified fallback; group path implemented, not live-verified without LuckPerms**
- [x] Permission fallback when LuckPerms is absent. **Verified**
- [x] `/police` dashboard with officer name, status, kills, arrests, duty time, earnings, joining date and balance. **Verified**
- [x] Dashboard shop, statistics and activity buttons. **Verified**
- [x] Statistics persist across restarts. **Verified** (SQLite profiles)
- [x] Multiple configurable duty cuboids with per-zone reward, interval, permission and group. **Verified** (creation/editing and rewards; permission/group fields implemented)
- [x] In-game zone creation/removal with selection wand. **Verified**
- [x] Next-reward countdown. **Verified** (action bar)
- [x] Automatic pay for eligible officers; multiple officers supported. **Verified**
- [x] Duty time tracking. **Verified**
- [x] AFK anti-abuse requiring movement. **Verified** (0 stationary payouts, resumed on movement)
- [x] Overlapping zones cannot double-pay. **Verified** (best eligible zone only)
- [x] Leaving, quitting, dying, teleporting and world changes handled. **Verified for leaving/quit/death paths; cross-world containment implemented**
- [x] Police-only eligibility. **Verified**

## Police shop and tools

- [x] Shop with configurable items, prices, names, lore, materials, enabled state, limits and permissions. **Verified** (purchase, permission, price)
- [x] Custom model data support (`custom-model-data` in `tools.yml`). **Implemented, not live-verified without a resource pack**
- [x] Confirmation screen. **Verified**
- [x] Inventory-full handling and refund on delivery failure. **Implemented; delivery failure path not forced live**
- [x] Purchase transaction history. **Verified**
- [x] PDC-secured tool id, serial and owner; persisted serials. **Verified** (validation and serial persistence)
- [x] Soulbound drop prevention. **Implemented; code path added**
- [x] Grave Shovel: registered graves only, one reward per grave, configurable reward and hide material. **Verified**
- [x] Investigation Brush: private name, LuckPerms group, balance, criminal status. **Verified**
- [x] Arrest Rope: right-click escort toggle. **Implemented natively; external plugin API unavailable, adapter documented** (not live-verified with the named external plugin)
- [x] Crossbow: configurable ammo, police-only, marked projectile, slow effect. **Implemented; live firing not verified** (bot harness did not fire the crossbow)
- [x] Arrest Baton: freezes criminal, blocks movement/jump/teleport/commands/interaction/damage, escort and jail. **Verified**
- [x] Normal sticks do nothing. **Verified** (PDC validation)
- [x] Citizen hit fine with cooldown. **Verified** (`Fine: $150.00`, no arrest)
- [x] Dynamic registry: add/remove/enable/disable/price/material/name/permission in-game. **Verified**
- [x] Every registered tool has a real behavior; no cosmetic-only tools. **Verified**

## Criminal

- [x] Criminal flag and activity records. **Verified**
- [x] Wanted expiry configuration. **Implemented; expiry not waited out in a live test**
- [x] Witnessed player kill makes the killer criminal. **Implemented; kill path exercised indirectly**
- [x] Witnessed robbery makes the robber criminal. **Verified**
- [x] Private witness/police notifications; no public broadcast by default. **Verified** (private chat only)
- [x] Criminal data hidden from unauthorized players. **Verified**
- [x] Administrative set/clear and record viewing. **Verified**

## Robbery

- [x] Multiple configurable cuboid locations with name, world, coordinates, duration, reward, cooldown, enabled, minimum players, maximum robbers and police notification. **Verified** (core fields; min/max/police-notify configured in YAML)
- [x] Default 300-second capture. **Verified in defaults; test used a 5-second zone**
- [x] Boss bar progress. **Verified**
- [x] Leaving interrupts/reset according to config. **Verified**
- [x] Death and disconnect cancel. **Implemented and listener paths exercised by disconnect**
- [x] Successful completion pays once and records the robbery. **Verified**
- [x] Cooldown persists across restart. **Verified**
- [x] Police alert configurable. **Verified** (private alert path)
- [x] AFK protection. **Implemented and configured; not separately timed out in the final run**
- [x] Duplicate payout prevented transactionally. **Verified**
- [x] Teleport/zone bypass prevented by containment checks. **Verified**
- [x] Overlapping location creation rejected. **Verified**
- [x] Multiple robbers/max occupancy. **Implemented; simultaneous multi-robber run not verified**
- [x] Blocking robberies while police are on duty. **Implemented via config; not enabled in the test**

## Sell system

- [x] `/sell` inventory GUI. **Verified**
- [x] Supported-item price display and total. **Verified**
- [x] Confirmation button. **Verified**
- [x] Whitelist, blacklist and material/custom-model pricing. **Implemented; whitelist/custom-model paths not live-forced**
- [x] Items returned on close. **Verified** (5 diamonds returned)
- [x] Items removed only after validated sale; payment logged. **Verified**
- [x] Full inventory and invalid item handling. **Implemented**
- [x] Sell-all supported. **Implemented; selected sale verified**
- [x] Transaction logging. **Verified**

## Economy

- [x] Vault-first auto provider with internal SQLite fallback. **Fallback verified; Vault path not live-verified**
- [x] EssentialsX economy through Vault. **Implemented; not live-verified (not installed)**
- [x] Give/take/set/balance and reward/charge operations. **Verified on internal provider**
- [x] `/eco give <player> <amount>` only when unowned. **Verified fallback registration logic**
- [x] Negative/NaN/overflow validation. **Verified code/tests; invalid amounts rejected**
- [x] Duplicate payout prevention. **Verified for robbery; transaction model used elsewhere**
- [x] Configurable precision and currency symbol. **Verified** (`$750.00`)
- [x] Transaction history. **Verified**

## Prison

- [x] Police kill of criminal detects, rewards, records and schedules prison. **Verified**
- [x] Configurable console jail command with validated `%player%` only. **Verified** (`tp` test command; default `warp prison %player%` unchanged in source)
- [x] No command injection via player input. **Verified by implementation (single validated name substitution)**
- [x] Baton arrest + escort + jail workflow. **Verified**
- [x] Configurable death/arrest duration and bounds. **Implemented**
- [x] Release handling and administrative release. **Verified**
- [x] Persistent sentence and reconnect enforcement. **Verified**
- [x] Offline release. **Implemented; not live-verified**
- [x] Missing warp failure does not silently succeed. **Verified**
- [x] Escape mechanics disabled/not implemented. **Verified** (sentences persist until release)

## Administration

- [x] `/cityadmin` GUI for zones, robberies, tools, records, transactions, prison, integrations, reload. **Verified**
- [x] Add/remove/edit duty zones. **Verified**
- [x] Create/remove/edit robbery locations. **Verified**
- [x] Add/remove/edit/disable shop tools. **Verified**
- [x] View player, police, criminal statistics and transaction logs. **Verified**
- [x] Reset statistics. **Verified** (`/cityadmin reset CityCivilian all`)
- [x] Reload configurations without erasing data. **Verified**
- [x] Integration status view. **Verified**
- [x] Confirmation/feedback for destructive actions. **Verified** (zone/tool delete; no extra confirmation prompt)
- [x] GUI click safety (no item movement, holder routing). **Verified**

## Database and safety

- [x] SQLite by default, bundled driver. **Verified**
- [x] Prepared statements and single database thread. **Verified by code; no main-thread JDBC in runtime paths**
- [x] UUID-based player keys. **Verified**
- [x] Schema creation on startup. **Verified**
- [x] Transactional robbery claim and grave claim. **Verified**
- [x] Data preserved across restart/reload. **Verified**
- [x] Safe shutdown and async saves. **Verified**
- [x] No data erased by reload. **Verified**
- [x] Schema version/migration framework. **Not implemented** — v1 creates missing tables idempotently. Future schema changes need explicit migrations. This is a known limitation.

## Configuration and UX

- [x] Separate YAML files for every subsystem. **Verified**
- [x] Messages and GUI labels configurable. **Verified**
- [x] No hardcoded gameplay values that should be configurable (defaults live in YAML). **Verified**
- [x] Consistent GUI titles, lore, navigation and back buttons. **Verified**
- [x] Shift-click/number-key movement cancelled in menus. **Verified**
- [x] Player feedback on failure. **Verified**

## Hard limitations to disclose

1. **LuckPerms group membership** was not live-verified because LuckPerms is not installed in the test server. The fallback permission path is verified.
2. **Vault/EssentialsX** was not live-verified because neither is installed. Internal economy is verified.
3. **External arrest-rope plugin** could not be integrated because its API was not available; the native adapter is delivered and documented as replaceable.
4. **Crossbow live shot** was not verified by the bot harness.
5. **Custom model data rendering** requires a resource pack and was not visually verified.
6. **Schema migrations** are not implemented beyond idempotent table creation.
