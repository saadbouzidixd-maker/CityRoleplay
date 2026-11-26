# Administration

Open the panel with `/cityadmin` (alias `/ca`). All actions require `city.admin`.

## Main panel

| Item | Section |
|---|---|
| Clock | Duty zones |
| Chest | Robbery locations |
| Stick | Dynamic police tools |
| Player head | Records |
| Paper | Recent transactions |
| Iron door | Prisoners and release |
| Comparator | Integration status |
| Lime dye | Reload all configuration |
| Barrier | Close |

The admin GUI uses a holder-based click router: clicks are cancelled unless a specific menu action handles them. Shift-clicks and number keys cannot move menu items.

## Duty zones

Create:

1. `/cityadmin wand`, then left-click and right-click the two corners.
2. `/cityadmin zones` -> green **Create**.
3. Enter id, display name, reward, and interval in chat.

Each zone stores a cuboid world, min/max coordinates, display name, reward, interval, permission, LuckPerms group, enabled state, movement requirement and movement timeout.

Editing a zone opens a menu with:

- Reward
- Interval
- Enable/disable
- Delete

Geometry and per-zone permission/group fields are edited in `afk-zones.yml`, followed by `/cityadmin reload`. Zone creation rejects an overlapping cuboid.

Operational rules:

- Only police receive duty pay.
- The best-paying eligible zone pays; overlapping zones never double-pay.
- The action bar shows the next reward countdown.
- Anti-AFK: when movement is required, standing still pauses pay until the player moves again. Entering by teleport does not count as movement.
- Leaving, quitting, dying, changing worlds, or stopping the task flushes accumulated duty time to SQLite.

## Robbery locations

Create the same way as a duty zone, then choose values for:

- Capture duration (minimum 5 seconds; default 300)
- Reward
- Cooldown
- Enabled state

The edit menu exposes duration, reward, cooldown, enable/disable and delete. Per-zone `min-online-players`, `max-robbers` and `police-notify` are YAML fields saved when the zone is edited.

Capture behavior:

- Entering a zone starts a boss-bar capture.
- Leaving cancels when `reset-on-leave` is true.
- AFK beyond `afk-timeout-seconds` cancels.
- Death or disconnect cancels the capture.
- Completion writes the robbery row and cooldown in one SQLite transaction, then pays once.
- A cooldown persists across restarts.
- Police are notified privately according to configuration.
- Overlapping robbery cuboids are rejected on creation.

## Dynamic tool registry

`/cityadmin tools` lists every tool with behavior id, price, permission, purchase limit and state.

**Create**: choose one of the registered behaviors, then enter id, material, price and permission. The tool is written to `tools.yml` with a real behavior attached.

**Edit**: price, display name, material, permission, enabled/disabled, delete.

Not every behavior setting is exposed in the GUI. Behavior-specific options are under the tool's `config:` block in `tools.yml`:

```yaml
grave-shovel:
  behavior: grave_shovel
  config:
    reward: 100.0
    hide-material: AIR
```

Supported behaviors:

| Behavior | Action |
|---|---|
| `grave_shovel` | Right-click a registered grave block to hide it and pay once. |
| `investigation_brush` | Right-click a player to privately read name, group, balance and criminal status. |
| `arrest_rope` | Right-click an arrested player to start/stop escorting. |
| `crossbow` | Police crossbow with configured ammo, projectile marker and slow effect. |
| `arrest_baton` | Hit a criminal to freeze/arrest; hit a citizen to be fined. |

Every tool item carries a PDC tool id, serial and owner. Serial values are persisted in `tool_serials`; validation checks the item, serial and owner. Purchased soulbound tools cannot be dropped.

### Developer extension

Implement `ToolBehavior`:

```java
public final class MyBehavior implements ToolBehavior {
    @Override public String id() { return "my_behavior"; }
    @Override public void onInteractBlock(ToolContext context, PlayerInteractEvent event) { ... }
    // Other callbacks are optional defaults.
}
```

Register it during startup with:

```java
toolRegistry.registerBehavior(new MyBehavior(...));
```

Then create a tool with behavior `my_behavior` through the admin GUI. It is a real behavior, not a renamed placeholder.

## Records and statistics

`/cityadmin records` supports All, Police and Criminals filters. A record shows:

- Kills
- Arrests
- Robberies
- Duty time
- Police earnings
- Balance
- Criminal status at view time
- Prison status at view time

Reset statistics from the command line:

```
/cityadmin reset <player> police
/cityadmin reset <player> criminal
/cityadmin reset <player> all
```

Police reset clears kills, arrests, duty seconds and police earnings. Criminal reset clears the criminal flag, wanted expiry, criminal-since and robbery count. Balances and transaction history are not erased by a statistics reset.

## Transactions

`/cityadmin transactions` shows the 45 most recent rows with player, type, amount, detail and local timestamp. Transaction types include:

`SELL`, `TOOL_PURCHASE`, `DUTY_REWARD`, `ROBBERY_REWARD`, `ARREST_REWARD`, `POLICE_KILL_REWARD`, `GRAVE_OPERATION`, `ECO_GIVE`, `ADMIN_GIVE`, `ADMIN_TAKE`, `ECO_TAKE`, and related administrative types.

## Prison

`/cityadmin prison` lists active sentences and releases on click, including offline players.

Command alternatives:

```
/cityadmin release <player>
/arrest release <player>   # releases the arrest state, not the prison sentence
```

Sentence configuration:

```yaml
prison:
  death-sentence-minutes: 10
  arrest-sentence-minutes: 15
  min-sentence-minutes: 1
  max-sentence-minutes: 1440
  jail-command: "warp prison %player%"
  release-command: ""
  release-world: ""
  enforce-on-join: true
  notify-on-join: true
```

The jail command runs from console. Only `%player%` is substituted, using a validated player name. Sentences persist in `player_profiles` and `prison_records`. On reconnect, a prisoner with time remaining is sent back to the configured destination and receives the remaining sentence.

If the configured warp command fails, the plugin reports `The prison warp command failed. Check the console.` and keeps the sentence; fix the warp/destination and release or re-jail the player.

## Criminal management

```
/cityadmin criminal set <player>
/cityadmin criminal clear <player>
```

Criminal status also comes from witnessed player kills, completed robberies, and baton arrests. Wanted expiry is `criminals.wanted-minutes`; `0` means no expiry. `criminals.public-broadcast: false` keeps status out of public chat.

## Economy management

```
/cityadmin eco give <player> <amount>
/cityadmin eco take <player> <amount>
/cityadmin eco set <player> <amount>
```

Amounts must be finite and non-negative. `take` refuses when the balance is insufficient. All changes go through the active economy provider and are logged when `economy.log-transactions` is true.

## Integrations

The integration panel displays:

- Vault availability and the active provider (`Internal` or `Vault`)
- LuckPerms availability and configured police group
- EssentialsX presence
- SQLite profile count

Vault/LuckPerms classes are loaded reflectively. Their absence degrades to the internal economy and permission fallback instead of failing plugin startup.

## Reload

`/cityadmin reload` (or the lime dye) reloads:

- All YAML files
- Messages
- Duty zones and robbery zones
- Tool registry and behavior list
- Economy provider
- Integration hooks

It flushes current duty time first and does not delete profiles, prison sentences, transactions or graves. Existing player inventories are not changed. If a tool definition is removed, previously issued items no longer validate.
