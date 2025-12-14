# Solo Rally Mode Implementation Plan

## Overview

Add a single-player **"Solo Rally"** mode where the player practices against a simulated wall. This mode uses the same grid/scoring system as co-op Rally but without a partner—the player is both hitter and receiver.

### Core Concept: Wall Bounce Simulation

The ball trajectory differs from multiplayer modes:
- **On Serve**: Player serves → ball bounces on their table side → "hits" the simulated wall (at net position) → bounces back on their table side → player hits
- **During Rally**: Player hits → ball travels to wall → wall "bounce" (no table bounce on way there) → ball bounces on their table side → player hits

### Key Differences from Co-op Rally

| Aspect | Co-op Rally | Solo Rally |
|--------|-------------|------------|
| **Players** | 2 (Bluetooth) | 1 (local) |
| **Network** | Required | None |
| **Turns** | Alternate players | Always player's turn |
| **Grid** | Each player has own grid | Single grid |
| **Flight Time** | 1.0× settings | 1.3× settings |
| **Stats** | Shared stats screen | Separate stats section |


---

## Proposed Changes

### Main Menu Screen

#### [MODIFY] [MainMenuScreen.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/ui/screens/MainMenuScreen.kt)

Add "Play Solo Rally" button:
- **Position**: Between "Play with a Friend" and "Settings"
- **Style**: Purple `Button` (matches "Play with a Friend")
- **No subtext** or icon decorations
- **Action**: Navigate directly to game screen with `gameMode = GameMode.SOLO_RALLY`

---

### Game Mode Strategy (Core Pattern)

Following the architecture constraint: *"New game modes MUST be added via `GameModeStrategy` implementations, NOT by adding conditionals to GameEngine."*

#### [NEW] [SoloRallyModeStrategy.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/core/game/SoloRallyModeStrategy.kt)

```kotlin
class SoloRallyModeStrategy : GameModeStrategy {
    override val modeName: String = "Solo Rally"
    override val gameMode: GameMode = GameMode.SOLO_RALLY
    override val hasRisk: Boolean = false  // No net/out risk (cooperative with wall)
    
    override fun createInitialModeState(): ModeState = ModeState.SoloRallyState.initial()
    
    // Grid, scoring, lives logic similar to RallyModeStrategy but single-player
    override fun onServe(state: GameState, swingType: SwingType): ModeState { /* ... */ }
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean): ModeState { /* ... */ }
    override fun onMiss(state: GameState, whoMissed: Player, isHost: Boolean): MissResult { /* ... */ }
    override fun getHitWindowShrink(state: GameState): Long { /* tier-based shrink */ }
    override fun getDisplayScore(state: GameState): Int? { /* return solo score */ }
    override fun getLives(state: GameState): Int? { /* return solo lives */ }
}
```

**Key Implementation Notes**:
- Reuse grid marking logic from `RallyModeStrategy` (same 3×3 grid, same line completions)
- No opponent grid tracking (single player)
- Same Happy number scoring tiers (confirmed: same as co-op Rally)
- Same lives system (3 starting lives, +1 on tier completion)
- Tier-based window shrinking works the same as co-op (50ms per tier)

---

#### [MODIFY] [GameState.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/core/game/GameState.kt)

Add to `GameMode` enum:
```kotlin
enum class GameMode {
    CLASSIC,
    RALLY,
    SOLO_RALLY  // NEW
}
```

Add helper property:
```kotlin
val soloRallyState: ModeState.SoloRallyState?
    get() = modeState as? ModeState.SoloRallyState
```

---

#### [MODIFY] [GameEngine.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/core/game/GameEngine.kt)

**Changes**:
1. Add `SOLO_FLIGHT_TIME_MULTIPLIER` as a **configurable setting** (default 1.3×):
   ```kotlin
   // Default value - actual value read from settings
   const val DEFAULT_SOLO_FLIGHT_TIME_MULTIPLIER = 1.3f
   ```

2. Update `getStrategy()` to return `SoloRallyModeStrategy` for `GameMode.SOLO_RALLY`

3. Modify flight time calculation:
   ```kotlin
   private fun getEffectiveFlightTime(): Long {
       val base = settings.flightTime
       return when (gameMode) {
           GameMode.SOLO_RALLY -> (base * SOLO_FLIGHT_TIME_MULTIPLIER).toLong()
           else -> base
       }
   }
   ```

4. **Turn Management**: In Solo Rally, `isMyTurn` is **always true** after serve. No turn alternation needed.

5. **Ball Trajectory**: Implement unique timing for wall bounce simulation (see Timing section below).

---

### Ball Trajectory & Timing

The ball travel in Solo Rally has a unique pattern:

#### Serve Sequence

```
Timeline: 0% ──────── 25% ──────── 75% ──────── 100%
          │           │            │             │
          Serve      Table       Table         Hit
          (hit)      bounce      bounce ←      window
                     (going)     (return)      opens
                         └── Wall at 50% ──┘
```

| Event | Timing | Sound |
|-------|--------|-------|
| Player serves | 0% | `hit.wav` |
| Table bounce (outgoing) | 25% | `bounce.wav` |
| Wall bounce | 50% | `bounce_wall.wav` (50% volume) |
| Table bounce (return) | 75% | `bounce.wav` |
| Hit window opens | 75% | Screen → Green |
| Miss timeout | 100% + window | `miss_*.wav` |

#### Rally Sequence (After Serve)

```
Timeline: 0% ──────── 40% ──────── 75% ──────── 100%
          │           │            │             │
          Hit        Wall         Table         Hit
          (swing)    bounce       bounce        window
                                               opens
```

| Event | Timing | Sound |
|-------|--------|-------|
| Player hits | 0% | `hit.wav` + swing type sound |
| Wall bounce | 40% | `bounce_wall.wav` (50% volume) |
| Table bounce (return) | 75% | `bounce.wav` |
| Hit window opens | 75% | Screen → Green |
| Miss timeout | 100% + window | `miss_*.wav` |

> [!NOTE]
> No table bounce on the *outbound* trip during rally (ball goes straight to wall)—only the return has a table bounce.

---

### Visual Feedback

#### Screen Color States

| State | Color | Description |
|-------|-------|-------------|
| Ball traveling | Yellow | From hit until near-window |
| Hit window open | Green | Window start → hit/miss |
| Just hit | Brief Yellow flash | Confirm hit registered |

**Timing for Green Screen**:
- Switch to green slightly *before* the hit window opens (to account for reaction time)
- Suggested: Green at 70% (5% before the 75% table bounce)

---

### Game Screen

#### [MODIFY] [GameScreen.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/ui/screens/GameScreen.kt)

**Solo Rally Mode Display**:
- **Avatar**: Show only local player's avatar, centered horizontally
- **No turn indicator**: Always player's turn, so no "Your Turn / Opponent's Turn" text
- **Instruction text**: "Hit!" instead of turn-based indicators
- **3×3 Grid**: Same as co-op Rally, but single grid (yours only)
- **Score counter**: Same animated slot-machine counter
- **Lives display**: Same heart icons

**Background Colors**:
- Follow the Yellow → Green pattern based on ball state
- No red (opponent turn) since there's no opponent

---

### Audio

#### [MODIFY] [AudioManager.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/audio/AudioManager.kt)

**Wall Bounce Sound**:
```kotlin
private const val SOLO_WALL_BOUNCE_VOLUME = 0.5f

fun playWallBounce() {
    play(R.raw.bounce_wall, SOLO_WALL_BOUNCE_VOLUME)
}
```

The `bounce_wall.wav` file already exists in `/res/raw/`.

---

### GameViewModel

#### [MODIFY] [GameViewModel.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/ui/GameViewModel.kt)

**Changes**:
1. **Start Solo Game Function**:
   ```kotlin
   fun startSoloRallyGame() {
       gameEngine.setGameMode(GameMode.SOLO_RALLY)
       gameEngine.startGame(isHost = true) // Always "host" in solo
       // No network setup needed
   }
   ```

2. **Bypass Network**: When `gameMode == SOLO_RALLY`, skip all network-related logic

3. **Always My Turn**: `isMyTurn` should always return `true` during active solo gameplay

4. **Schedule Bounce Sounds**: Schedule wall bounce sound at 40% of flight time, table bounce at 75%

---

### Game Over Screen

#### [NEW] [SoloGameOverScreen.kt](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/ui/screens/SoloGameOverScreen.kt)

Create a dedicated game over screen for Solo Rally:

| Element | Details |
|---------|-------------|
| Layout | Single column, centered |
| Score | Your solo score |
| Stats shown | Lines Cleared, Grid Level, Longest Rally |
| High Score | Solo high score (separate from co-op Rally) |
| Buttons | Play Again, Main Menu |

**Display**:
- Show "New High Score!" highlight if applicable (check against `solo_high_score`)
- Single column layout (no partner stats)
- "Play Again" restarts solo game
- "Main Menu" returns to main menu
- No "Switch Mode" button

---

### Settings & Stats Screen

#### [MODIFY] Settings Screens

**Stats Screen** - Add new Solo Rally section:
```
Rally Mode (Co-op)
- High Score
- Longest Rally
- Lines Cleared
- Highest Level

Solo Rally Mode        ← NEW SECTION
- High Score
- Longest Rally
- Lines Cleared
- Highest Level

Classic Mode
- Wins / Losses
- Longest Rally
```

**Game Parameters Screen (Advanced Section)** - Add Solo Flight Time Multiplier slider:
- **Label**: "Solo Rally Flight Time Multiplier"
- **Range**: 1.0× to 2.0× (step 0.1)
- **Default**: 1.3×
- **Info tooltip**: "Adjusts how much longer the ball travels in Solo Rally mode. Higher values give more time between hits."

**Flight Time tooltip** (existing) - Update to mention:
> "Controls how long the ball is in the air between hits.
> 
> **Note**: In Solo Rally mode, flight time is multiplied by the Solo Rally Multiplier setting (default 1.3×)."

---

### String Resources

#### [MODIFY] [strings.xml](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/res/values/strings.xml)

```xml
<!-- Solo Rally Mode -->
<string name="play_solo_rally">Play Solo Rally</string>
<string name="solo_rally">Solo Rally</string>
<string name="solo_hit_instruction">Hit!</string>

<!-- Stats -->
<string name="stats_solo_section_title">Solo Rally Mode</string>
<string name="stats_solo_high_score">High Score</string>
<string name="stats_solo_longest_rally">Longest Rally</string>
<string name="stats_solo_lines_cleared">Lines Cleared</string>
<string name="stats_solo_highest_level">Highest Level</string>

<!-- Flight Time Note -->
<string name="solo_flight_time_note">In Solo Rally mode, flight time is 1.3× longer to compensate for hitting more frequently.</string>
```

*(Add translations for all supported languages)*

---

### Persistence (Stats Storage)

#### [MODIFY] [SharedPreferences keys](file:///c:/Users/blair/Documents/dev/antigravity/AirRally/app/src/main/java/com/air/pong/ui/GameViewModel.kt)

Add separate keys for Solo Rally stats:
```kotlin
private const val KEY_SOLO_HIGH_SCORE = "solo_rally_high_score"
private const val KEY_SOLO_LONGEST_RALLY = "solo_rally_longest_rally"
private const val KEY_SOLO_LINES_CLEARED = "solo_rally_total_lines_cleared"
private const val KEY_SOLO_HIGHEST_LEVEL = "solo_rally_highest_grid_level"
```

---

### Avatar Unlocks

**Decision**: Solo Rally stats do **not** contribute to avatar unlocks.

- Solo Rally is tracked separately from co-op Rally
- No avatar unlock thresholds for Solo Rally at this point
- This keeps solo mode as a practice/casual experience
- Can revisit adding solo-specific unlocks in a future update

---

### Exit Confirmation

When pressing back during a solo game, show the same confirmation dialog:
- "You quitting?"
- "Yeah" → Game over screen (records stats)
- "Nevermind" → Continue game

---

## Implementation Checklist

### Phase 1: Core Engine (No UI) ✅
- [x] Add `SOLO_RALLY` to `GameMode` enum
- [x] Create `SoloRallyModeStrategy.kt`
- [x] Update `GameEngine.getStrategy()` to handle new mode
- [x] Add `SOLO_FLIGHT_TIME_MULTIPLIER` constant
- [x] Implement Solo Rally flight time calculation
- [x] Implement wall bounce timing logic
- [x] Add solo-specific `isMyTurn` handling (always true)

### Phase 2: Audio & Timing ✅
- [x] Add `playWallBounce()` to AudioManager
- [x] Implement serve timing sequence (25% / 50% / 75%)
- [x] Implement rally timing sequence (40% / 75%)
- [x] Schedule bounce sounds at correct percentages

### Phase 3: UI Integration
- [x] Add "Play Solo Rally" button to MainMenuScreen
- [x] Modify GameScreen for solo display (single avatar, centered)
- [x] Implement Yellow/Green background color states
- [x] Add solo mode check to exit confirmation dialog
- [x] Add Solo Flight Time Multiplier slider to Game Parameters (Advanced section)
- [x] Add Solo Rally subsection to "How to Play" (beneath Classic Mode section)

### Phase 4: Stats & Persistence ✅
- [x] Add Solo Rally stats SharedPreferences keys
- [x] Modify Stats screen to show Solo Rally section
- [x] Update `GameOverScreen.kt` for Solo Rally (reused existing screen instead of new file)
- [x] Add Flight Time tooltip text update

4a. fixes 
- [x] Solo mode sounds missing. add back in the same sounds as co-op mode for line clears and grid clears.
- [x] misses are not registering (should time out and say miss (no swing) but stays green until swing and does a whiff sound instead)
- [x] default to flight time multiplier --> change to adder 0.5 seconds
- [x] make the solo flight time adder slider title "Solo Flight Time Adder" 0.2 seconds min to 0.8 seconds max
- [x] make the solo flight time adder slider (i) tooltip "Increases how long the ball is in the air between hits. Longer flight times for solo mode since you are hitting more frequently."
- [x] fix info popup for flight time multiplier (700ms). Right now it still refers to the solo mode multiplier instead of the adder.
- [x] fix game over screen for solo mode. after game or after pressing back, you are sent straight to the main menu. you should go to the solo mode game over screen instead
- [x] green and yellow background colors timing are off. greeen seems to appear too early. it should change to green right about when the ball bounces. please clarify how it's working now and then propose a change to make it work as intended.
- [x] green and yellow background colors timing have been corrected in solo mode. but the HIT text is still off. please align that with the colors changes.
- [x] add settings button to the game over screen for solo mode.

- [x] make sure that the hit window shrink is working in solo mode. confirm what the minimum hit window can be in solo mode. and determine if thater are any impossible shots with that hit window and the flight time multiplier and adder.
  - **VERIFIED**: Hit window shrink is working correctly via `SoloRallyModeStrategy.getHitWindowShrink()` (50ms per tier)
  - **Minimum Hit Window**: 300ms (hard floor enforced in `GameState.calculateHitWindow()`)
  - **No Impossible Shots**: All combinations of settings remain playable. Hardest case (min flight 600ms × 0.3 Hard Smash = 180ms flight + 300ms window) is challenging but achievable.

### Phase 5: Polish & Testing ✅
- [x] Update PLAN.md with Solo Rally mode documentation
- [x] Add tips for Solo Rally mode in ScrollingTips (8 tips total)
- [x] Add solo mode tests (`SoloRallyModeTest.kt` - 13 unit tests)
- [x] Create `docs/rules/solo_mode.md` documentation
- [x] Manual testing: full game flow
- [x] Manual testing: stats persistence
- [x] Manual testing: high score tracking


### Phase later: Localization
- [ ] Add all new strings to `strings.xml`
- [ ] Add translations for all 9 supported languages

---

## Verification Plan

### Manual Tests

1. **Main Menu → Start Solo Game**
   - Press "Play Solo Rally" button
   - Verify game starts with solo configuration
   - No Bluetooth dialogs or connection prompts

2. **Serve Flow**
   - Serve the ball
   - Verify table bounce sound at ~25%
   - Verify wall bounce sound at ~50%
   - Verify second table bounce at ~75%
   - Verify screen turns green near window open

3. **Rally Flow**
   - Hit during green window
   - Verify grid cell gets marked
   - Verify score updates
   - Verify next cycle: wall bounce at 40%, table at 75%

4. **Miss Handling**
   - Intentionally miss (don't swing)
   - Verify life decreases
   - Verify miss sound plays
   - Verify game continues if lives remain

5. **Game Over**
   - Lose all 3 lives
   - Verify game over screen shows solo layout
   - Verify stats are correct
   - Verify "New High Score!" appears if applicable
   - Verify "Play Again" restarts solo game

6. **Stats Persistence**
   - Complete a solo game
   - Exit to main menu
   - Check stats screen for Solo Rally section
   - Verify high score persisted

7. **Exit Confirmation**
   - Start solo game, press back
   - Verify "You quitting?" dialog appears
   - Test both "Yeah" and "Nevermind" paths

### Automated Tests (Future)

- Unit tests for `SoloRallyModeStrategy` scoring
- Unit tests for flight time multiplier calculation
- Integration tests for complete solo game flow

---

## Files Changed Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `GameState.kt` | MODIFY | Add `SOLO_RALLY` to `GameMode` enum, add helper property |
| `GameEngine.kt` | MODIFY | Add flight multiplier, update strategy selection |
| `SoloRallyModeStrategy.kt` | NEW | Solo Rally mode strategy implementation |
| `GameViewModel.kt` | MODIFY | Add solo game start, stats persistence for Solo Rally |
| `MainMenuScreen.kt` | MODIFY | Add "Play Solo Rally" button |
| `GameScreen.kt` | MODIFY | Solo mode display (single avatar, colors) |
| `GameOverScreen.kt` | MODIFY | Added Solo Rally layout (single-column, solo-specific buttons) |
| `AudioManager.kt` | MODIFY | Add wall bounce sound function |
| `strings.xml` (+ translations) | MODIFY | Add all new strings |
| `StatsScreen.kt` | MODIFY | Add Solo Rally stats section |
| `GameParamsSettingsScreen.kt` | MODIFY | Add Solo Flight Time Multiplier slider (Advanced section), update tooltip |
| `HowToPlayScreen.kt` | MODIFY | Add Solo Rally subsection beneath Classic Mode |
| `ScrollingTips.kt` | MODIFY | Add Solo Rally tips |
| `PLAN.md` | MODIFY | Document Solo Rally mode |


