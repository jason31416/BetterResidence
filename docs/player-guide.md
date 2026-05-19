# BetterResidence Player Guide

BetterResidence lets you protect land by creating claims. A claim is a selected 3D box in the world. Inside your claim, visitors are blocked from actions they do not have permission for, such as breaking protected blocks.

The main command is `/res`. You can also use `/betterresidence`, `/betterres`, or `/bres`.

## Create Your First Claim

1. Hold the claim selection tool. By default, this is a wooden hoe.
2. Left-click one corner of the area you want to protect.
3. Right-click the opposite corner.
4. Watch the particle preview and action bar message. It shows whether the selection can be claimed and how much it costs.
5. Run:

```text
/res create <name>
```

Example:

```text
/res create home
```

If the claim is valid, money is taken from your balance and the claim is created. If it overlaps another claim, is too large for your balance, or breaks a server limit, the plugin will tell you.

## Subclaims

If your selection is completely inside an existing claim that you can manage, `/res create <name>` creates a subclaim instead of a normal top-level claim.

Subclaims are useful for splitting a large claim into smaller areas, such as shops, rooms, farms, or rented plots. Subclaims are free to create, but they must stay inside the parent claim.

## See Claim Information

Stand inside a claim and run:

```text
/res info
```

You can also look up a claim by name or UUID:

```text
/res info <claim>
```

To list your claims:

```text
/res list
```

To list another player's claims:

```text
/res list <player>
```

## Trust Friends

Stand inside your claim and run:

```text
/res trust <player>
```

This adds the player to the default trusted group for that claim.

To remove their trust:

```text
/res untrust <player>
```

Some servers may configure extra groups, such as a blacklist group. If so, you can put a player into a specific group:

```text
/res trust <player> <group>
```

## Change Claim Permissions

Claim owners can decide which group is allowed to perform an action.

```text
/res set <permission> <group>
```

Common permissions are:

| Permission | Meaning |
| --- | --- |
| `block.break` | Break blocks |
| `block.place` | Place blocks |
| `block.interact` | Interact with blocks |
| `entity.damage` | Damage entities |
| `entity.interact` | Interact with entities |
| `admin` | Manage the claim |

Examples:

```text
/res set block.break trusted
/res set block.place trusted
/res set admin owner
```

Group names may be translated or changed by the server, so use tab completion or check `/res info` if a group name does not work.

## Add Or Remove Claim Areas

A claim can have more than one protected area.

To add another selected area to the claim you are standing in:

```text
/res area add
```

To add the selected area to a specific claim:

```text
/res area add <claim>
```

To see the areas in the claim you are standing in:

```text
/res area list
```

To remove the area you are standing in:

```text
/res area remove
/res area remove confirm
```

The plugin requires confirmation before removing an area.

## Delete A Claim

Stand inside a claim you manage and run:

```text
/res remove
/res remove confirm
```

The confirmation expires after a short time. If the claim has subclaims, direct subclaims are promoted to the deleted claim's parent.

## Visual Hints

When you hold the selection tool, nearby claims may appear as particle outlines. Your current selection also appears as a particle box.

Selection colors show the result:

| Color Type | Meaning |
| --- | --- |
| Available | You can create the claim |
| Limited | The shape is valid, but money or server limits block it |
| Conflict | The selection overlaps or conflicts with another claim |

When you enter or leave a claim, the plugin can show an action bar message and briefly flash the claim border.

## Quick Command List

```text
/res create <name>
/res info [claim]
/res list [player] [page]
/res trust <player> [group]
/res untrust <player>
/res set <permission> <group>
/res area add [claim]
/res area list
/res area remove
/res area remove confirm
/res remove
/res remove confirm
```
