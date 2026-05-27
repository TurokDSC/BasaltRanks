# BasaltRanks - Visual Rank Badge Generator

**Version:** 2.0.0 | **Author:** Turok_SS | **API:** Paper 1.20.x - 26.x
**License:** All Rights Reserved (Free to use)

---

## What is BasaltRanks?

BasaltRanks generates pixel-art rank badges that display in chat, nametags, and the tab list using Minecraft's resource pack system. Each badge is rendered as a custom Unicode character via bitmap fonts, so players see crisp badges right in the game.

**Features at a glance:**
- Create custom rank badges with 5 visual styles (Flat, 3D, Glass, Metallic, Neon)
- 26 pre-made templates across 4 categories
- In-game GUI editor with color picker
- Web-based badge editor (browser UI, auto-start)
- Automatic resource pack generation and serving
- LuckPerms auto-assignment by group
- PlaceholderAPI support (8 placeholders)
- Full REST API for external tools
- Folia compatible (FoliaCompat scheduling)

---

## Installation

1. Download `BasaltRanks-2.0.0.jar`
2. Place in your server's `plugins/` folder
3. Start/restart the server
4. Edit `plugins/BasaltRanks/config.yml` to set your public IP under `resourcepack.server-ip`
5. Done! Use `/badge templates` to browse and create your first badge

### Requirements

| Requirement | Version |
|-------------|---------|
| Paper (or forks like Purpur) | 1.20.x - 26.x |
| Java | 21+ |
| Folia | Supported (optional) |

### Optional Dependencies

| Plugin | Purpose |
|--------|---------|
| LuckPerms | Auto-assign badges by permission group |
| PlaceholderAPI | Use badge data in other plugins |

---

## Commands

### /badge (aliases: /badges, /ranks)

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `/badge list` | `basaltranks.list` | List all badges |
| `/badge info <id>` | `basaltranks.list` | Show badge details |
| `/badge preview <id>` | `basaltranks.admin` | Preview a badge in chat |
| `/badge create <id> [template]` | `basaltranks.admin` | Create a badge (from template or manual) |
| `/badge create <id> <text> <bg> <textcolor> [gradient] [border] [style]` | `basaltranks.admin` | Create with full params |
| `/badge edit <id>` | `basaltranks.admin` | Open GUI editor for a badge |
| `/badge delete <id>` | `basaltranks.admin` | Delete a badge |
| `/badge assign <player> <badge>` | `basaltranks.admin` | Assign badge to a player |
| `/badge remove <player>` | `basaltranks.admin` | Remove badge from a player |
| `/badge templates` | `basaltranks.admin` | Browse template gallery (GUI) |
| `/badge render <id\|all>` | `basaltranks.admin` | Render badge(s) to PNG file |
| `/badge web [on\|off\|password]` | `basaltranks.admin` | Toggle web editor |
| `/badge reload` | `basaltranks.admin` | Reload configuration |

### /basaltranks (aliases: /branks)

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `/basaltranks version` | `basaltranks.use` | Show plugin version |
| `/basaltranks reload` | `basaltranks.admin` | Reload configuration |

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `basaltranks.*` | OP | All permissions |
| `basaltranks.admin` | OP | Admin commands (create, edit, delete, assign, remove, templates, reload) |
| `basaltranks.use` | Everyone | Basic usage |
| `basaltranks.list` | Everyone | List and view badge info |
| `basaltranks.web` | OP | Access web editor |

---

## Badge Styles

BasaltRanks supports 5 visual rendering styles:

| Style | Description |
|-------|-------------|
| **Flat** | Clean solid/gradient background with subtle shadow |
| **3D** | Raised button look with bottom edge and top highlight |
| **Glass** | Frosted glass effect with gloss overlay |
| **Metallic** | Chrome/metal sheen with bevel border and highlight band |
| **Neon** | Glowing neon sign on dark background |

---

## Templates

26 pre-made templates organized in 4 categories:

### Staff
`owner` `admin` `mod` `helper` `builder` `dev`

### Rank
`vip` `vip-plus` `mvp` `mvp-plus` `elite` `legend` `titan`

### Special
`og` `beta` `verified` `nitro` `booster` `veteran`

### Fun
`pro` `noob` `goat` `afk` `streamer` `youtuber`

**Usage:** `/badge create mybadge owner` creates a badge using the "owner" template.

---

## Configuration Reference

### config.yml

```yaml
# Module toggles (disable any module)
modules:
  badge: true          # Core badge CRUD
  resourcepack: true   # Pack generation + HTTP server
  display: true        # Chat, nametag, tab display
  luckperms: true      # LuckPerms auto-assignment
  editor: true         # GUI editor + /badge commands
  web-editor: true     # Web-based editor panel

# Resource pack HTTP server
resourcepack:
  server-ip: "localhost"     # CHANGE to your public IP/domain!
  port: 8080                 # HTTP port
  force-pack: true           # Force players to accept
  prompt-message: "..."      # Message shown on pack prompt
  auto-regenerate: true      # Auto-regen on badge changes

# Web editor panel (http://your-ip:8080/web)
web-editor:
  auto-start: true           # Start web editor on enable
  password: "change-me"      # CHANGE before exposing!

# Badge display toggles
display:
  chat: true                 # Badge in chat messages
  nametag: true              # Badge above player heads
  tab: true                  # Badge in tab list
  badge-suffix: " "          # Space after badge char

# LuckPerms group -> badge mapping
luckperms:
  group-badges:
    owner: "owner"
    admin: "admin"
    mod: "mod"
    helper: "helper"
    vip: "vip"
    default: ""              # Empty = no badge

# GUI sounds
sounds:
  volume: 0.5
  pitch: 1.0
  open: BLOCK_NOTE_BLOCK_CHIME
  click: UI_BUTTON_CLICK
  success: ENTITY_PLAYER_LEVELUP
  error: ENTITY_VILLAGER_NO

# Update checker
update-check: true
```

### messages.yml

All 42 plugin messages are customizable. Uses MiniMessage format (Adventure API).

---

## Web Editor

BasaltRanks includes a built-in web editor accessible from any browser.

### Setup

1. Set `resourcepack.server-ip` to your server's public IP
2. Change `web-editor.password` from `"change-me"`
3. Enable: `/badge web on`
4. Open `http://your-ip:8080/web` in your browser
5. Login with your password

### Features

- **Badge Manager** - Create, edit, delete badges with live pixel-art preview
- **Template Browser** - Browse and create from 26 pre-made templates
- **Player Assignment** - Assign/remove badges from online players
- **LuckPerms Panel** - Map LuckPerms groups to badges
- **Pack Regeneration** - Regenerate the resource pack from the browser

### REST API

The web editor exposes a REST API for custom integrations:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/badges` | List all badges |
| POST | `/api/badges` | Create a badge |
| PUT | `/api/badges/{id}` | Update a badge |
| DELETE | `/api/badges/{id}` | Delete a badge |
| GET | `/api/badges/{id}/preview` | Render badge as PNG |
| GET | `/api/templates` | List all templates |
| POST | `/api/badges/from-template` | Create from template |
| GET | `/api/players` | List online players |
| POST | `/api/assign` | Assign badge to player |
| POST | `/api/remove` | Remove badge from player |
| POST | `/api/regenerate` | Regenerate resource pack |
| GET | `/api/preview?displayText=...&bgColor=...` | Live preview |
| GET | `/api/luckperms/status` | LuckPerms status |
| GET | `/api/luckperms/groups` | Group mappings |
| PUT | `/api/luckperms/groups` | Update mappings |

**Authentication:** All API calls require `Authorization: Bearer <password>` header.

---

## PlaceholderAPI

If PlaceholderAPI is installed, the following placeholders are available:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basaltranks_badge%` | Badge ID | `owner` |
| `%basaltranks_text%` | Display text | `OWNER` |
| `%basaltranks_style%` | Badge style | `metallic` |
| `%basaltranks_unicode%` | Unicode character | (custom char) |
| `%basaltranks_has_badge%` | Has badge? | `true` / `false` |
| `%basaltranks_priority%` | Badge priority | `50` |
| `%basaltranks_color%` | Background color | `#FF0000` |
| `%basaltranks_text_color%` | Text color | `#FFFFFF` |

All placeholders return an empty string if the player has no badge assigned.

---

## LuckPerms Integration

When LuckPerms is installed, BasaltRanks can automatically assign badges based on a player's primary group.

### How it works

1. Configure group-to-badge mappings in `config.yml` under `luckperms.group-badges`
2. When a player joins, their primary LuckPerms group is checked
3. The corresponding badge is automatically assigned
4. When a player's rank changes, the badge updates in real-time

### Real-time updates

BasaltRanks subscribes to LuckPerms' `UserDataRecalculateEvent`, so badge changes happen instantly when a player's group changes (e.g., via `/lp user <name> parent set <group>`).

---

## How Resource Packs Work

BasaltRanks generates a Minecraft resource pack containing:

1. **PNG images** - Each badge is rendered as a pixel-art image
2. **Font configuration** - Maps each badge to a Unicode Private Use Area character (U+E100+)
3. **Pack ZIP** - A valid resource pack ZIP file

The embedded HTTP server serves this pack to players on join. When you create or edit a badge, the pack is automatically regenerated and re-sent to all online players.

### Port Configuration

The HTTP server runs on port `8080` by default. Make sure this port is:
- Open in your firewall
- Not used by another service
- Forwarded if behind NAT

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Players can't download pack | Set `server-ip` to your public IP, not `localhost` |
| Port already in use | Change `resourcepack.port` in config |
| Badges show as squares | Player needs to accept the resource pack |
| Pack not updating | Run `/badge reload` or `/badge render all` |

---

## Creating Badges

### From Template (Recommended)

```
/badge create mybadge owner
```

This creates a badge with ID `mybadge` using the `owner` template's colors and style.

### From Scratch

```
/badge create mybadge OWNER #FF0000 #FFFFFF #8B0000 #FFD700 metallic
```

Parameters: `<id> <text> <bg-color> <text-color> [gradient] [border] [style]`

### Using the GUI Editor

1. Create a badge: `/badge create mybadge owner`
2. Open editor: `/badge edit mybadge`
3. Click items to modify properties
4. Click "Save & Generate" when done

### Using the Web Editor

1. Enable: `/badge web on`
2. Open `http://your-ip:8080/web`
3. Click "+ Create Badge" or pick a template
4. Use the live preview to design your badge
5. Click "Save Badge"

---

## FAQ

**Q: Does this work with Geyser/Bedrock players?**
A: The resource pack system works with Bedrock players via Geyser, but badge rendering depends on the Bedrock client's font support.

**Q: Can players have multiple badges?**
A: Currently each player can have one badge at a time. The highest-priority badge is shown if using LuckPerms auto-assignment.

**Q: Does this conflict with other chat plugins?**
A: BasaltRanks uses `EventPriority.HIGH` for chat, so it runs after most chat plugins. It wraps the existing chat renderer rather than replacing it.

**Q: How many badges can I create?**
A: There's no hard limit. Each badge uses one Unicode PUA character starting at U+E100. The practical limit is 6,400+ characters.

**Q: Can I use this with BungeeCord/Velocity?**
A: BasaltRanks works per-server. Badge data is stored per-server in `badges.yml`. For cross-server badges, you'd need to copy the data folder.

---

## Support

- **Issues:** Report bugs on the resource page
- **Discord:** Join the Basalt community
- **Author:** Turok_SS (Basalt)
