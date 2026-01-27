# Fayaz’s Lockout — Changelog

## 0.0.1 — Dec 22, 2025
**Death Lockout**
- Created initial version.
- Start a game using:
  - `/lockout start <goal> <p1> <p2>`
- `/lockout reset` can be used at any time to end and reset the game.
- Required on both client and server.

---

## 0.0.2 — Dec 22, 2025
**Death Lockout**
- Initial full release.
- Added backing GUI.
- Added custom icons per death type to easily track claimed deaths.

---

## 0.0.3 — Dec 23, 2025
**Death Lockout**
- Version 1.1.0 now supports more than 2 players.
- Add players with:
  - `/lockout add <player> <color>`
- Player colors are configurable.
- `/lockout status` can be checked at any time.

---

## 0.0.4 — Dec 23, 2025
**Death Lockout**
- Added clearer error messages and improved command descriptions when starting a game.

---

## 0.0.7 — Dec 29, 2025
### Major Overhaul  
**Renamed**
- **Death Lockout** → **Fayaz’s Lockout**
- Now supports multiple game modes.

**Added — New Game Modes**
- **Kills Mode**: Race to kill unique mob types.
- **Death Mode Submodes**:
  - `SOURCE` (damage type)
  - `MESSAGE` (death text)

**Game Start Improvements**
- 3-second countdown with player freeze (3, 2, 1, GO!)
- Players teleported to spawn point.
- Inventories cleared; health and hunger restored.

**Spawn Points**
- `/lockout spawnpoint [x y z]`
- Defaults to world spawn if not set.

**Pause System**
- Auto-pause when a participating player disconnects.
- Title screen shows who the game is waiting for.
- Auto-resume when they rejoin.
- Manual controls:
  - `/lockout pause`
  - `/lockout unpause`

**Player Management**
- Default color assignment (color now optional).
- Enforced unique player colors.
- `/lockout player modify <player> <color>`
- `/lockout player remove <player>`

**HUD**
- Pause indicator with title overlay.
- Mode display (Deaths / Kills).

**Command Structure Changes**
- `/lockout add` → `/lockout player add <player> [color]`
- `/lockout mode <kills|death> [source|message]`
- `/lockout start` no longer requires a mode parameter.
- `/lockout stop` ends the match but keeps configuration.
- `/lockout reset` wipes everything.
- Configuration persists between matches until reset.

**Status Command**
- Now shows:
  - Active / paused state
  - Mode and sub-mode
  - Goal
  - Players and scores

**Fixed**
- More reliable death tracking in SOURCE mode.
- Prevented color conflicts.

---

## 0.0.8 — Dec 29, 2025
### 3 New Game Modes Added
- **Armor**: Race to wear a full set of any armor type, or a single piece.
- **Foods**: Race to eat unique foods.
- **Advancements**: Race to earn unique advancements.

---

## 0.0.9 — Jan 9, 2026
- Added `/lockout join` to quickly join with a default color.
- Player additions, goal changes, and mode changes now broadcast to all players.
- Added **Mixed Mode**, allowing goals from any mode.
- Fixed advancement icons not displaying correctly.
- Fixed advancements being awarded early.

---

## 0.1.0 — Jan 9, 2026
- Updated to run on Fabric 26.1 Snapshot 2.
- Player color changes during an active game now broadcast immediately.
- Added a new, more efficient GUI container.
- Players now race toward a larger central goal.
- Added a Goal Tracker UI with tooltips (default keybind: `U`).
- Refactored client-side logic into smaller files.
- Added snarky messages for:
  - Dying in a different mode
  - Earning an advancement already claimed
- Fixed Drowned mob displaying incorrectly.
- Added sounds for when you or an opponent scores a goal.
- Removed duplicate message on joining a lockout.
- Reverted a debug setting that allowed solo game starts.

---

## 0.1.1 — Jan 9, 2026
- Witch laughing sound now plays with snarky messages.
- Center goal box is now properly centered and enlarged.
- Default game mode is now **Mixed**.

---

## 0.1.3 — Jan 21, 2026
- Added new **Game Setting: Switch**
  - After scoring a goal, players swap:
    - Inventory
    - Health
    - Spawn point
    - XP
    - Location
    - Hunger, saturation, and effects
- Added new **Game Mode: Breed**
  - Race to breed unique mobs.
- Added icon overlays in Mixed mode to differentiate goal types.
- Added copper armor to Armor mode.
- Updated to Fabric 26.1 beta 4.
- Fixed victory box background not filling properly.
- Mixed mode goal types now broadcast from the server for accurate icons.

---

## 0.1.4 — Jan 21, 2026
- Nested configuration under `/lockout configure <configuration>`.
- Added Mixed mode include/exclude configuration.
  - Everything included by default.
  - Exclusions remove items from the inclusion list.
  - Examples:
    - `/lockout configure mixed exclude Foods`
    - `/lockout configure mixed include Death`
- `/lockout status` now shows Mixed mode inclusions/exclusions.
- Sub-modes (Armor, Death) are now configurable:
  - `/lockout configure <mode> <sub-mode>`
- Fixed Armor mode not functioning correctly in Mixed mode.

---

## 0.1.5 — Jan 23, 2026
- Snarky messages can now be disabled via config:
  - `/lockout configure snarky_messages false`
- Default value: `true`.

---

## 0.1.6 — Jan 26, 2026
- **Armor Mode Improvements**
  - Turtle helmet now counts as a full armor set.
  - Armor must be equipped via right-click from the hotbar to count (prevents lag from inventory polling).
  - Fixed spam of snarky messages when wearing already-claimed armor.
- Fixed icon display for bred animals.
- Added overlay to the victory box in Mixed mode.
- Game start now revokes all advancements and recipes.
- Updated lockout screen to use consistent HUD colors.
- Victory box now races toward the center when present.
- Added experimental configuration:
  - `reset_worldDefault(false)`
- `reset_world` currently:
  - Resets weather cycle
  - Attempts to kill all mobs and loot
  - Planned: time-of-day and chunk resets.

---

## 0.2.0 — Jan 27, 2026
- Added support for adding all players at once:
  - `/lockout player add @a`
- Goals can no longer be changed mid-game.
- Lockout screen and HUD now share unified code.
- UI now wraps correctly on smaller screens and updates live when resizing.