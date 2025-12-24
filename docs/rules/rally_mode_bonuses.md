# Rally Bonus System Implementation

## Overview

This document provides full context for implementing new bonus mechanics for Rally mode (both Co-op and Solo). The goal is to add engaging layers of strategy without overwhelming the core gameplay experience.

**Default State**: All bonus mechanics (except Rally Momentum and Grid Upgrades) are **disabled by default**. Players can enable them individually in the game settings to customize their experience.

**UI Indicators**: The 6 bonus chips displayed to the left of the avatar show a white border when the corresponding bonus is enabled:
- Top row (always enabled): Rally Momentum 🏓 | Grid Upgrades 🔢
- Middle row (disabled by default): Spin 💫 | Copy Cat 🐱
- Bottom row (disabled by default): Golden Squares 🪙 | Power Outage 💡

---

## Bonus Mechanics - Full Descriptions

### 1. Spin Shots Bonus

**Concept**: Reward players for using varied spin techniques during a rally. Spin is detected from phone gyroscope data during swings.

**How Spin is Detected**:
- Uses `peakGy` (Y-axis gyroscope) and `peakGz` (Z-axis gyroscope) from swing data
- Thresholds from SENSORS.md: `|peakGy| > 10` for vertical spin, `|peakGz| > 8` for horizontal spin
- Classification produces 8 distinct spin types:
  - **Single-axis**: TOP, BACK, LEFT, RIGHT
  - **Combined**: TOP_LEFT, TOP_RIGHT, BACK_LEFT, BACK_RIGHT
  - **NONE**: Below threshold, no spin detected\n\n> [!NOTE]\n> **Accessibility**: Players using "Tap-to-Hit" mode can trigger spin shots by **swiping** on the grid cells instead of tapping. Swipe direction maps directly to spin type (e.g., Swipe Up = Top Spin).

**Multiplier Bonus**:
- +0.1 multiplier per **unique** spin type used while avatar is spinning
- Maximum: +0.5 (when 5 unique types achieved)
- **Temporary**: Only active while avatar is spinning. Resets on miss (unless infinite spin achieved).
- **Applied to scoring**: Points earned during rally include this multiplier

**Avatar Animation**:
- When player uses ANY spin shot (not NONE), their avatar starts/continues spinning
- **Duration**: +15 seconds added per spin shot (any type, even repeats)
- **Speed**: Increases with more unique spin types (3s/rotation → 0.5s/rotation)
- **Infinite Spin**: If player achieves 5 distinct spin types while avatar is still spinning, it spins forever for the rest of the **game** (not just current rally).
- **Partner Sync**:
  - Left player's avatar spins **clockwise** (toward center)
  - Right player's avatar spins **counter-clockwise** (toward center)
  - Partner's avatar spin state is synced via network.

**Audio**:
- `spin_infinite.wav` plays when infinite spin is achieved.

**Examples**:
- Hit 1 TOP → Avatar spins 15s, +0.1 multiplier while spinning
- Hit 10 TOPs → Each adds 15s to spin time, but still only +0.1 multiplier (same type)
- Hit TOP, then BACK → Each adds 15s, +0.2 multiplier while spinning
- Hit TOP, BACK, RIGHT, LEFT, BACK_RIGHT → Each adds 15s, +0.5 multiplier, avatar spins forever

**Action Log**:
- Include spin type in hit messages:
  - No spin: `"You Hit: SOFT LOB"`
  - With spin: `"You Hit: SOFT LOB (top spin)"`, `"Partner Hit: HARD FLAT (back spin)"`

---

### 2. Copy Cat Bonus

**Concept**: Reward cooperative play by encouraging players to mirror each other's shot patterns.

#### Co-op Mode Rules

**Leader/Follower Dynamic**:
- The serving player becomes the "leader" for that point
- The receiving player is the "follower"
- Leader's shots are recorded in a 9-shot sequence buffer

**Copy Detection**:
- After leader hits, if follower's next shot uses the **exact same SwingType** (same force AND same type), it counts as a "copy"
- Example: Leader hits SOFT_LOB → Follower must hit SOFT_LOB to copy
- Spin is NOT copied (only Force + Type).

**Copy Cat Tier System** (Persistent Multiplier):
- Multiplier earned is **persistent** - survives misses, only clears on game over
- Cannot re-claim same tier; must reach higher tier to increase bonus

| Tier | Copies Required | Unique Types | Multiplier |
|------|-----------------|--------------|------------|
| 1 | 5 consecutive | 3+ | +0.1 |
| 2 | 6 consecutive | 4+ | +0.2 |
| 3 | 7 consecutive | 5+ | +0.3 |
| 4 | 8 consecutive | 6+ | +0.4 |
| 5 | 9 consecutive | 7+ | +0.5 (max) |

**Extra Life Bonus**:
- Completing a full 9/9 Copy Cat sequence awards an **extra life**.

#### Solo Mode Rules (CPU Sequence Challenge)

**CPU-Generated Sequence**:
- Every 10-20 hits, CPU generates a 9-cell target sequence
- CPU ensures sequence meets tier uniqueness requirements (7+ unique types for max tier)
- Player is always the "follower" - must hit the target cells in order
- **Indicators**:
  - 🎯 shows the next target cell
  - � shows completed cells in sequence (e.g. `1�`, `2�`)
  - 🐾 shows leader's cells (Co-op only)

**Sequence Completion**:
- Hit target cell → Progress to next target in sequence
- Hit wrong cell → Sequence breaks, cooldown starts
- Miss (timing) → Sequence breaks, cooldown starts
- Complete all 9 → Award tier bonus based on unique types, cooldown starts

**Solo Tier System** (same as Co-op):
| Sequence Progress | Unique Types | Multiplier |
|-------------------|--------------|------------|
| 5+ | 3+ | +0.1 |
| 6+ | 4+ | +0.2 |
| 7+ | 5+ | +0.3 |
| 8+ | 6+ | +0.4 |
| 9 | 7+ | +0.5 |

**Visual & Audio Feedback**:
- **Indicators**: 
  - 🐾 (Leader's cells - Co-op)
  - � (Follower's copied cells)
  - 🎯 (Next target - Solo/Follower)
- **Pattern Break**: Flashes `✖️` symbol on broken cells (3 pulses).
- **Audio**: 
  - `copy_cat_meow.wav` on bonus achievement.
  - `copy_cat_miss.wav` on pattern break (if sequence > 4).

---

### 3. Special Squares

**Concept**: Dynamic grid squares that appear during gameplay with positive or negative effects.

#### Square Types

| Type | Effect | Visual |
|------|--------|--------|
| **BANANA PEEL** 🍌 | Subtract `10 × current_tier` points, reduce gold multiplier by 0.1 | Light gray fill, pulsing gray border |
| **LANDMINE** 💥 | Rally ends immediately, lose 1 life, breaks infinite spin | Pinkish red fill, pulsing red border |
| **GOLDEN** 🪙 | 3× normal points for that cell | Gold fill, pulsing gold border |

#### Spawn Rules

- **When**: Special squares spawn on tier upgrade (when grid clears and advances to next tier)
- **Not on Tier 0**: No special squares on the first grid
- **Spawn Rates** (Categorical Distribution - single roll):
  - 20% GOLDEN: Max 1 per grid (33% center cell preference)
  - 40% PENALTY: Max 1-2 per grid
    - 50% of penalty = Banana only
    - 25% of penalty = Landmine only
    - 25% of penalty = Both (must share row or column)
  - 40% NORMAL: No special squares
- **Placement**: When spawning both penalty squares, they must share row OR column (never diagonal pair)

#### Interaction Rules

- **Serve Exempt**: Special square effects are NOT triggered on serve hits
- **GOLDEN Line Clear Bonus**:
  - 1 line clear including golden cell: +0.1 persistent multiplier
  - 2 line clears: +0.2 additional (+0.3 total)  
  - 3+ lines or X-clear: +0.2 additional (+0.5 max)
  - Golden square reverts to normal after clearing
- **Banana Peel**: 
  - Deducts points. Visual feedback: negative points fly up from cell.
  - Reduces golden multiplier by 0.1 (cannot go below 0)
  - **Score Floor**: Score cannot go below 0 (banana penalty is clamped)
  - Audio: `banana_slip.wav`
- **Landmine**:
  - Ends rally immediately (unless Shield is active). 
  - Breaks infinite spin if earned (severe penalty).
  - Note: Logic implemented via `HitResult.MISS_LANDMINE`.
  - Audio: `explosion.wav`
- **Early/Late Miss Behavior**:
  - Special square effects apply even when swinging early or late on a penalty square
  - **Landmine**: Explosion sound plays, infinite spin is broken (if earned), life is still lost
  - **Banana Peel**: Points are deducted, golden multiplier reduced by 0.1, life is still lost

---

### 4. Shield Mechanic

**Concept**: A defensive power-up that forgives one timing miss.

**How to Earn Shield**:
- Clear a grid that contains a BANANA or LANDMINE square (without hitting any of those penaly squares) → Player earns Shield
- This rewards "playing through" the penalty square

**Shield Effect**:
- Forgives the next MISS_EARLY or MISS_LATE
- Converts the miss to a valid HIT at the window boundary
- One-time use, consumed immediately
- Cannot stack multiple shields

**Visual**:
- Pulsing grid/hash pattern overlay on top of player's avatar
- Semi-transparent (50-80% opacity) so avatar is visible underneath like a force field
- Alternating blue/white color animation cycling every ~1.5 seconds
- Grid pattern with horizontal, vertical, and diagonal lines in circular mask
- Fades out when consumed

---

### 6. Power Outage Mechanic

**Concept**: A penalty that triggers when a player relies too heavily on a single shot type. Encourages shot variety and adds tension when playing dangerously.

#### Trigger Conditions

- **Threshold**: Hitting the same cell 10 times (per cell) triggers a power outage for that player
- **Tracking**: Each grid cell has an independent hit counter (0-9)
- **Warning**: When a cell reaches 7+ hits, a 🔌# indicator appears showing hits remaining until outage

#### Warning Indicators

- **Visual**: 🔌3, 🔌2, 🔌1 appears in the bottom-left corner of cells approaching the threshold
- **Purpose**: Gives players warning to diversify their shots before triggering an outage

#### Power Outage Effect

- **Dark Overlay**: A dark semi-transparent overlay (92% opacity) covers grid section of the screen with rounded corners. Other UI is not hidden.
- **Message**: "⚡ POWER OUTAGE ⚡" displayed at top with recovery instructions
- **Progress**: Shows 💡 or 🔌 icons for each of the 5 required recovery shots
- **Recovery Counter**: Displays current progress (e.g., "2 / 5")

#### Recovery Conditions

- **Unique Cells**: Player must hit 5 **different** cells consecutively during the outage
- **Progress Tracking**: 💡 appears on cells that have been successfully hit during recovery
- **Reset on Duplicate**: If the player hits the same cell twice during recovery, progress resets to 0
- **Reset on Miss**: If the player misses a timing window, the outage clears but no bonus is awarded

#### Recovery Rewards

- **Hit Window Bonus**: Successfully recovering via 5 unique hits grants +50ms to the hit window
- **Accumulation**: This bonus accumulates across multiple recoveries (50ms, 100ms, 150ms, etc.)
- **Cap**: The bonus is capped at the base difficulty level (cannot exceed starting hit window)
- **Stats**: 💡# indicator shows in the bonus column after each successful recovery

#### Persistence

- **Cell Counts Reset**: On miss OR on successful recovery, all cell hit counts reset to 0
- **Bonuses Preserved**: The `powerOutagesClearedCount` and `powerOutageWindowBonus` persist across misses
- **Per-Player**: In Co-op mode, each player tracks their own outage state independently

---

### 5. Multiplier System

**How Multipliers Stack**:
All multipliers are **additive** on top of the base momentum multiplier:

```
Total = Momentum + Spin + CopyCat + Golden

Where:
- Momentum: 1.0 to 1.5 based on rally length (10 hits = +0.1, up to 50+ = +0.5)
- Spin: 0 to 1.0 (temporary, resets when avatar stops)
- CopyCat: 0 to 0.5 (persistent, survives misses)
- Golden: 0 to 0.5 (persistent, from golden line clears)
```

**Maximum Possible**: 1.5 + 1.0 + 0.5 + 0.5 = **3.5×**

**Visual Feedback**:
- **Animated Ball**:
  - 1-1.5×: White ball, no tail
  - 1.5-2×: Orange ball (pulsing flame color), no tail
  - 2-2.5×: Orange ball with white tail
  - 2.5-3×: Rainbow ball with white tail
  - 3+×: Rainbow ball with rainbow tail
- **MomentumBadge** (circle + flame icon):
  - 1×: Invisible
  - 1.1-1.5×: White pulsing circle, white pulsing flame
  - 1.5-2×: White pulsing circle, orange pulsing flame
  - 2-2.5×: Orange pulsing circle, orange pulsing flame
  - 2.5-3×: Rainbow pulsing circle, white pulsing flame
  - 3+×: Rainbow pulsing circle, rainbow pulsing flame
- Dynamic "STREAK" label shows most recent bonus type earned

---

## Current Implementation Status

### ✅ COMPLETED PHASES

**Phase 1: Foundation & State Management**
- Enhanced `SpinType` with gyro detection.
- `RallyState`/`SoloRallyState` extended with bonus fields.
- `StatsRepository` tracking.

**Phase 2: Spin Shot Detection**
- Full support in Co-op and Solo.
- Avatar animations (Clockwise/Counter-Clockwise) & Network Sync.
- Infinite spin logic & sound.

**Phase 3: Copy Cat Bonus**
- Co-op Leader/Follower dynamic & Tier system.
- Solo CPU sequence challenge.
- Visual indicators (🐾, 🐱, 🎯, ✖️ Flash).
- Network sync for Co-op.
- Audio cues (`meow`, `miss`).

**Phase 4: Special Squares**
- Spawning logic (Categorical distribution).
- Hit interactions (Banana points, Landmine rally end, Golden multiplier).
- Shield earning & consumption logic.
- Audio cues (`explosion`, `banana_slip`).

### 🛠 PENDING / TODO

**Bugs**
- [x] **Shield**: ~~still seeing times where I hit a banana or a land mine square and the shield is still awarded after clearing that grid. It should only be awarded if the grid is cleared cleanly (without hitting a penalty square(s)).~~ **FIXED** - Added `penaltyHitThisTier` tracking for both banana and landmine hits.
- [x] **Copy Cat**: ~~copy cat _in solo mode_ isn't applying the bonus multiplier and it's also not showing the 🐱5/9 indicator.~~ **FIXED** - Added intermediate tier bonus calculation when continuing a sequence.

**Optimization & Testing**
- [x] **Spin**: Add unit tests.
- [x] **Copy Cat**: Add unit tests for tier calc, pattern detection.
- [x] **Special Squares**: Unit tests for spawning/shield.
- [x] **Manual Verification**: Full playthroughs to tune rates/feel.

**UI Enhancements**
- [x] **Dynamic STREAK Label**: Shows most recent bonus type earned on HUD. Special bonuses (SPIN/COPYCAT/GOLD) display for 10 seconds after being earned, then fall back to "Rally!" to prevent the label from being stuck on one bonus type during long rallies.
- [x] **Rainbow Ball**: Trigger animation when total multiplier ≥ 1.5. rainbow between 1.5 and 2, white streak behind ball from 2 to 2.5. rainbow streak behind ball from 2.5 and above.

**Documentation**
- [x] Update `docs/rules/rally_mode.md` and `solo_mode.md` with new rules.
- [x] Update "How to Play" screen in app.
- [x] add new scrolling tips for bonus mechanics in game 

---

## Key Constants

Located in `SwingExtensions.kt` SpinType companion object:
```kotlin
const val Y_THRESHOLD = 10.0f
const val Z_THRESHOLD = 8.0f
const val MAX_SPIN_MULTIPLIER_BONUS = 0.5f  // Max +0.5 at 5 unique types
const val SPIN_BONUS_PER_TYPE = 0.1f
const val SPIN_DURATION_PER_SHOT_SECONDS = 15L  // Any spin shot adds 15s (not just new types)
const val INFINITE_SPIN_THRESHOLD = 5  // 5 unique types = infinite spin for rest of game
```

Located in `SpecialSquareType.kt`:
```kotlin
// Categorical distribution for spawn type selection
const val GOLDEN_CATEGORY_CHANCE = 0.20f    // 20% golden
const val PENALTY_CATEGORY_CHANCE = 0.40f   // 40% penalty (remaining 40% = normal)

// Sub-probabilities within penalty category
const val PENALTY_BANANA_ONLY_CHANCE = 0.50f    // 50% of penalties
const val PENALTY_LANDMINE_ONLY_CHANCE = 0.25f  // 25% of penalties (remaining 25% = both)

const val GOLDEN_CENTER_CHANCE = 0.33f       // Golden 33% chance for center cell
const val BANANA_PENALTY_PER_TIER = 10       // Points deducted per tier
const val GOLDEN_POINTS_MULTIPLIER = 3       // 3x points for golden cell hit
```

Located in `SwingExtensions.kt` PowerOutageConstants object:
```kotlin
const val TRIGGER_HITS = 10           // 10 hits on same cell triggers outage
const val WARNING_THRESHOLD = 7       // Show 🔌 warning at 7+ hits
const val RECOVERY_UNIQUE_CELLS = 5   // Hit 5 unique cells to restore power
const val WINDOW_BONUS_MS = 50L       // +50ms hit window per successful recovery
```

---

## Architecture Diagram

```
                    GameEngine.processSwing()
                           │
                           ▼
                   SpinType.fromGyro(peakGy, peakGz)
                           │
                           ▼
              ┌────────────┴────────────┐
              │                         │
    modeStrategy.onServe()    modeStrategy.onHit()
              │                         │
              ▼                         ▼
    ┌─────────────────────────────────────────────┐
    │  RallyModeStrategy / SoloRallyModeStrategy  │
    │  - Track distinctSpinTypes                  │
    │  - Calculate spinMultiplierBonus            │
    │  - Update avatarSpinEndTime                 │
    │  - Set lastBonusType                        │
    └─────────────────────────────────────────────┘
                           │
                           ▼
                     RallyState / SoloRallyState
                           │
                           ▼
                    GameScreen.kt
                           │
                           ▼
                  RallyAvatarPair()
                  - mySpinTypeCount
                  - myAvatarSpinEndTime  
                  - myIsSpinningIndefinitely
                           │
                           ▼
                  Avatar Rotation Animation
```
