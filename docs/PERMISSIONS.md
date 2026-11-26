# Permissions

## Permission nodes

| Node | Default | Grants |
|---|---|---|
| `city.admin` | op | Full admin GUI and every `/cityadmin` action, including `city.police`, `city.sell`, `city.robbery` and `city.arrest` |
| `city.police` | false | Police dashboard, duty eligibility, police tools base access |
| `city.sell` | true | `/sell` |
| `city.robbery` | true | Starting a robbery capture |
| `city.arrest` | false | Arrest controls in addition to police membership |
| `city.tools.grave` | false | Buying/using the Grave Shovel |
| `city.tools.brush` | false | Buying/using the Investigation Brush |
| `city.tools.rope` | false | Buying/using the Arrest Rope |
| `city.tools.crossbow` | false | Buying/using the Police Crossbow |
| `city.tools.baton` | false | Buying/using the Arrest Baton |

`city.admin` is a parent of the gameplay permissions in `plugin.yml`, but not every tool permission. Grant tool nodes explicitly to police ranks.

## Police membership

Police membership is checked in this order:

1. If LuckPerms is installed and the configured group exists: membership in `police.group` (`POLICE` by default).
2. Otherwise: the `city.police` permission.

The group check uses LuckPerms' actual user/group data. The plugin never edits unrelated permissions; `/cityadmin police add|remove` only adds/removes the configured group.

## Recommended LuckPerms setup

```
/lp creategroup police
/lp group police permission set city.police true
/lp group police permission set city.arrest true
/lp group police permission set city.tools.grave true
/lp group police permission set city.tools.brush true
/lp group police permission set city.tools.rope true
/lp group police permission set city.tools.crossbow true
/lp group police permission set city.tools.baton true

/lp creategroup sergeant parent add police
/lp creategroup captain parent add sergeant

/lp user Steve parent add police
```

Grant `city.admin` only to trusted administrators:

```
/lp creategroup admin
/lp group admin permission set city.admin true
/lp user Admin parent add admin
```

## Configurable group

`police.yml`:

```yaml
police:
  group: POLICE
  permission-fallback: city.police
```

Change `group` to match your server's police group. Group names are case-insensitive. If the group does not exist, officers fall back to the permission, so grant `city.police` regardless.

## Per-zone requirements

Duty zones can additionally require:

```yaml
permission: "city.duty.station"
group: "POLICE"
```

- `permission`: a Bukkit permission node the officer must have.
- `group`: an additional LuckPerms group requirement; it is ignored if LuckPerms is unavailable.

Robbery zones do not have a permission field; starting a robbery requires `city.robbery` or `city.admin`.

## Tool permissions

Each tool definition in `tools.yml` has a `permission` field:

```yaml
permission: city.tools.baton
```

Both purchase and use require it unless empty. The GUI edit screen can change this value in-game.

## Security notes

- The Investigation Brush requires police membership and its tool permission; it does not expose criminal status publicly.
- The Arrest Baton is validated by PDC, serial and owner; a renamed normal stick does nothing.
- The police shop is reachable only through `/police` and still re-checks the tool permission at purchase time.
- `city.admin` allows ownership bypass in `/arrest jail` for an existing arrest session, but the `/arrest` command itself still requires police membership. It does not make a non-police player eligible for duty rewards.
