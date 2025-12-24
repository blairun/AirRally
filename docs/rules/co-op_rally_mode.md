# Rally Mode Rules

Rally Mode is a **cooperative (co-op)** game mode where both players work together to keep a rally going and maximize their combined score. Unlike Classic Mode (competitive 1v1), Rally Mode emphasizes teamwork and sustained rallies.

## Lives System

| Aspect | Details |
|--------|---------|
| **Starting Lives** | 3 |
| **Losing a Life** | Either player misses a shot |
| **Game Over** | When lives reach 0 |
| **Extra Life** | +1 when either player completes a grid tier for the first time globally |
| **Low Lives Warning** | Pulses Pink (2 lives) or Red (1 life) |
- Tier-up sound plays every tier up (just on that person's device), but only on the device that acheived grid clear.

> [!NOTE]
> The extra life is awarded only once per tier, globally. If Player A completes Tier 1 first, Player B completing Tier 1 later does NOT award another life.

## Scoring

Rally Mode uses the **Triangular Numbers** sequence for tier-based scoring progression:

**Formula**: `T(n) = n × (n + 1) / 2`

### Points Per Hit by Tier

| Tier | Points | Tier | Points | Tier | Points | Tier | Points |
|------|--------|------|--------|------|--------|------|--------|
| 0 | 1 | 5 | 21 | 10 | 66 | 15 | 136 |
| 1 | 3 | 6 | 28 | 11 | 78 | 16 | 153 |
| 2 | 6 | 7 | 36 | 12 | 91 | 17 | 171 |
| 3 | 10 | 8 | 45 | 13 | 105 | 18 | 190 |
| 4 | 15 | 9 | 55 | 14 | 120 | 19 | 210 |

> [!NOTE]
> Triangular numbers provide smooth, unlimited progression. Points continue increasing indefinitely as tiers rise.

### How Points Are Earned

1. **Serves**: Award tier-based points (based on lowest cell tier) but do NOT mark the grid
2. **Regular Hits**: Award points AND mark a grid cell (determined by swing type)
3. **Line Completion**: Bonus points for completing lines (see below)
4. **Points Animation**: Each hit shows an animated popup ("+X" flying to score), displaying "LINE!" or "LINES!" when lines are cleared

## The 3×3 Grid System

Each player has their own individual 3×3 grid. When you hit the ball, one cell gets marked based on your swing type:

```
┌───┬───┬───┐
│ 0 │ 1 │ 2 │  ← Row 0 (indices 0-1-2)
├───┼───┼───┤
│ 3 │ 4 │ 5 │  ← Row 1 (indices 3-4-5)
├───┼───┼───┤
│ 6 │ 7 │ 8 │  ← Row 2 (indices 6-7-8)
└───┴───┴───┘
  ↑   ↑   ↑
 Col Col Col
  0   1   2
```

### Swing Type → Grid Cell Mapping

The 9 swing types map to the 9 grid cells:

| Swing Type | Grid Cell |
|------------|-----------|
| Soft Flat | 0 |
| Medium Flat | 1 |
| Hard Flat | 2 |
| Soft Lob | 3 |
| Medium Lob | 4 |
| Hard Lob | 5 |
| Soft Smash | 6 |
| Medium Smash | 7 |
| Hard Smash | 8 |

## Line Completion

### The 8 Lines

There are 8 possible lines that can be completed:

- **3 Rows (Horizontal)**: 0-1-2, 3-4-5, 6-7-8
- **3 Columns (Vertical)**: 0-3-6, 1-4-7, 2-5-8
- **2 Diagonals**: 0-4-8 (main), 2-4-6 (anti)

### Line Bonuses

When a line is completed, bonus points are awarded. Bonuses scale with tier and vary by line type (using triangular number offsets):

| Tier | Horizontal T(n+2) | Vertical T(n+3) | Diagonal T(n+4) |
|------|-------------------|-----------------|-----------------|
| 0 | 3 | 6 | 10 |
| 1 | 6 | 10 | 15 |
| 2 | 10 | 15 | 21 |
| 3 | 15 | 21 | 28 |
| 4 | 21 | 28 | 36 |
| ... | ... | ... | ... |

> [!TIP]
> Diagonal lines are worth the most! Aim for the corners (0, 2, 6, 8) and center (4).

### Special Bonuses

- **Double-Line Bonus**: When 2+ lines are cleared in a single hit, up to 2 random base-tier cells get bonus points automatically
- **X-Clear**: Completing BOTH diagonals (0-4-8 AND 2-4-6) simultaneously triggers an **instant tier upgrade**, even if not all cells are upgraded

## Tier Progression

### How Tiers Work

1. All cells start at **Tier 0**
2. When a line is cleared:
   - Those cells get unmarked
   - Cells at the minimum tier get upgraded to the next tier
3. When ALL 9 cells reach the next tier:
   - **Grid Complete!**
   - Grid resets (all cells unmarked, all at new tier)
   - **+1 Extra Life** awarded (first time globally)

### Tier Upgrade Effects

| Effect | Details |
|--------|---------|
| Grid Reset | All cells unmarked, all upgraded to new tier |
| Extra Life | +1 life (first global completion only) |
| Hit Window | Shrinks by 50ms per tier achieved |
| Points | All future hits worth more (see scoring table) |

## Difficulty Mechanics

### No Risk in Rally Mode

In Classic Mode, aggressive shots have Net% and Out% risks. In Rally Mode, **both risks are eliminated** (set to 0%). Players are cooperating and placing shots nicely for each other.

> [!IMPORTANT]
> All difficulty in Rally Mode comes from timing windows, not shot risk.

### Hit Window Shrinking

The hit window (time you have to return the ball) shrinks as you progress:

- **Tier-based shrink**: 50ms per tier achieved globally
- **Minimum floor**: 300ms (window never shrinks below this)
- **Swing type shrink**: Hard smashes/lobs still shrink the partner's window

### Example Window Calculation

- Base difficulty: 800ms
- At Tier 3: 800 - (3 × 50) = **650ms window**
- At Tier 6: 800 - (6 × 50) = **500ms window**
- Capped at floor: Never below 300ms

## Server Rotation

After losing a life, the server alternates between players (same as Classic mode for fairness).

## Bonus Mechanics

Rally Mode features several bonus mechanics to help you score higher:

- **Spin Shots**: Use different spin types (Top, Back, Left, Right) to build a multiplier.
- **Copy Cat**: Mimic your partner's shots to build a persistent multiplier.
- **Special Squares**: Watch out for special squares on the grid!
  - 🪙 **Golden**: 3x points.
  - 🍌 **Banana Peel**: Penalty points.
  - 💥 **Landmine**: Ends the rally (unless you have a Shield), also kills spin if you have it.

For full details on these mechanics, see [Rally Mode Bonuses](rally_mode_bonuses.md).

## Strategy Tips

> [!TIP]
> - Use **Lobs** to give your partner more time on difficult returns
> - Coordinate swing types to fill different grid cells
> - The **center cell (4)** is in both diagonals - very valuable!
> - Go for X-Clears when possible for instant tier upgrades
