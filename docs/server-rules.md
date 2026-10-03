# Nexus Client – rules for server owners

Nexus never includes unfair features (no reach, autoclicker, x-ray, kill aura…). Some servers still
don't allow things like minimaps, freelook, fullbright or auto GG. Your server can switch off any
Nexus mod for players on it. Rules can only switch mods **off**, never on.

## How it works

When a Nexus player joins, send a plugin message on the channel **`nexusclient:rules`**. The message
is plain JSON text (UTF-8):

```json
{"block": ["minimap", "freelook", "fullbright"]}
```

- `"*blockable"` switches off every mod Nexus marks as server-blockable
  (currently: freelook, fullbright, waypoints, minimap, autoGG).
- Sending a new message replaces the old one. Sending `{"block": []}` allows everything again.
- The rules end when the player leaves your server.
- The player sees one chat line: `[Nexus] This server has switched off: Minimap, Freelook`, and the
  mods show "Blocked" in the Nexus menu.

## Mod ids

HUD: `fps`, `cps`, `ping`, `keystrokes`, `coordinates`, `direction`, `biome`, `armorHud`, `heldItems`,
`potionEffects`, `itemCounter`, `clock`, `memory`, `serverAddress`, `combo`, `speedrunTimer`,
`packDisplay`

PvP: `toggleSprint`, `toggleSneak`, `crosshair`, `hitColor`, `blockOutline`, `oldAnimations`,
`hitboxes`, `smallItems`, `itemPhysics`, `lowFire`, `noHurtCam`, `particles`, `enchantGlint`

Visual: `zoom`, `motionBlur`, `freelook`, `fullbright`, `timeChanger`, `weatherChanger`, `nametags`,
`waypoints`, `minimap`

Chat: `chatTimestamps`, `copyChat`, `chatStacking`, `longerChat`, `mentionHighlight`, `chatFilter`

Utility: `screenshotUploader`, `nickHider`, `autoGG`, `streamerMode`

## Example (Paper/Spigot plugin)

Nexus tells the server it listens on the channel, so send the rules when that happens:

```java
getServer().getMessenger().registerOutgoingPluginChannel(this, "nexusclient:rules");

@EventHandler
public void onChannel(PlayerRegisterChannelEvent event) {
    if (event.getChannel().equals("nexusclient:rules")) {
        byte[] rules = "{\"block\": [\"minimap\", \"freelook\"]}".getBytes(StandardCharsets.UTF_8);
        event.getPlayer().sendPluginMessage(this, "nexusclient:rules", rules);
    }
}
```

## Partner Servers

Nexus Partner Servers can instead have their rules built into Nexus itself (no plugin needed).
