# Solo Rally Mode Rules

Solo Rally Mode is a **single-player practice** game mode where you practice against a simulated wall. It uses the same grid and scoring mechanics as Co-op Rally, but designed for solo play.

## Overview

| Aspect | Solo Rally | Co-op Rally |
|--------|------------|-------------|
| **Players** | 1 (local) | 2 (Bluetooth) |
| **Network** | None | Required |
| **Turns** | Always your turn | Alternate between players |
| **Grid** | Single grid (yours) | Two grids (you + partner) |
| **Flight Time** | Base + 500ms adder | Base time only |

## Wall Bounce Simulation

In Solo Rally, the ball bounces off a simulated wall instead of traveling to a partner:

### Serve Sequence

```
Timeline: 0% ──────── 25% ──────── 50% ──────── 75% ──────── 100%
          │           │            │            │             │
          Serve      Table       Wall         Table          Hit
          (hit)      bounce      bounce       bounce ←       window
                     (going)                  (return)       opens
```

| Event | Timing | Sound |
|-------|--------|-------|
| Player serves | 0% | `hit.wav` |
| Table bounce (outgoing) | 25% | `bounce.wav` |
| Wall bounce | 50% | `bounce_wall.wav` (50% volume) |
| Table bounce (return) | 75% | `bounce.wav` |
| Hit window opens | 75% | Screen → Green |

### Rally Sequence (After Serve)

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

> [!NOTE]
> No table bounce on the *outbound* trip during rally (ball goes straight to wall)—only the return has a table bounce.

## Lives System

| Aspect | Details |
|--------|---------|
| **Starting Lives** | 3 |
| **Losing a Life** | Miss a shot |
| **Game Over** | When lives reach 0 |
| **Extra Life** | +1 when completing a grid tier |

## Scoring

Solo Rally uses the same **Triangular Numbers** sequence as Co-op Rally:

**Formula**: `T(n) = n × (n + 1) / 2`

### Points Per Hit by Tier

| Tier | Points | Tier | Points | Tier | Points |
|------|--------|------|--------|------|--------|
| 0 | 1 | 5 | 21 | 10 | 66 |
| 1 | 3 | 6 | 28 | 11 | 78 |
| 2 | 6 | 7 | 36 | 12 | 91 |
| 3 | 10 | 8 | 45 | 13 | 105 |
| 4 | 15 | 9 | 55 | 14 | 120 |

### How Points Are Earned

1. **Serves**: Award tier-based points but do NOT mark the grid
2. **Regular Hits**: Award points AND mark a grid cell (determined by swing type)
3. **Line Completion**: Bonus points for completing lines
4. **Points Animation**: Each hit shows an animated popup ("+X" flying to score), displaying "LINE!" or "LINES!" when lines are cleared

## The 3×3 Grid System

Same as Co-op Rally - when you hit the ball, one cell gets marked based on your swing type:

```
┌───┬───┬───┐
│ 0 │ 1 │ 2 │  ← FLAT swings (Soft/Med/Hard)
├───┼───┼───┤
│ 3 │ 4 │ 5 │  ← LOB swings (Soft/Med/Hard)
├───┼───┼───┤
│ 6 │ 7 │ 8 │  ← SMASH swings (Soft/Med/Hard)
└───┴───┴───┘
  ↑   ↑   ↑
Soft Med Hard
```

### Line Bonuses

| Tier | Horizontal T(n+2) | Vertical T(n+3) | Diagonal T(n+4) |
|------|-------------------|-----------------|-----------------|
| 0 | 3 | 6 | 10 |
| 1 | 6 | 10 | 15 |
| 2 | 10 | 15 | 21 |

> [!TIP]
> Diagonal lines are worth the most! Aim for the corners (0, 2, 6, 8) and center (4).

### Special Bonuses

- **Double-Line Bonus**: 2+ lines cleared in one hit = up to 2 bonus cells
- **X-Clear**: Both diagonals (0-4-8 AND 2-4-6) = **instant tier upgrade**

## Flight Time Adder

Solo Rally adds extra time to the base flight time since you're hitting more frequently:

| Setting | Value | Effect |
|---------|-------|--------|
| **Default Adder** | 500ms | Base + 500ms |
| **Range** | 200ms - 800ms | Adjustable in Advanced Settings |

This compensates for the faster pace of solo play where you don't have breaks while a partner hits.

## Difficulty Mechanics

### No Risk in Solo Mode

Like Co-op Rally, Solo Rally has **no net/out risk**. You're practicing, not competing!

### Hit Window Shrinking

The hit window shrinks as you progress:

- **Tier-based shrink**: 50ms per tier achieved
- **Minimum floor**: 300ms (window never shrinks below this)

### Example Window Calculation

- Base difficulty: 800ms
- At Tier 3: 800 - (3 × 50) = **650ms window**
- At Tier 6: 800 - (6 × 50) = **500ms window**
- Capped at floor: Never below 300ms

## Stats Tracking

Solo Rally tracks stats **separately** from Co-op Rally:

| Stat | Description |
|------|-------------|
| **High Score** | Best solo score achieved |
| **Longest Rally** | Most consecutive hits |
| **Lines Cleared** | Total lines completed in solo mode |
| **Highest Level** | Best tier reached |

> [!IMPORTANT]
> Solo Rally stats do NOT contribute to avatar unlocks. Avatars are earned through Co-op Rally and Classic modes only.

## Strategy Tips

> [!TIP]
> - **Find your rhythm**: The wall returns at a consistent pace
> - **Practice all 9 swing types**: You need variety to fill the grid
> - **Start with LOBs**: Slower shots give you more time to react
> - **Watch for green**: The screen turns green when it's time to swing
> - **Use as warmup**: Great practice before playing with a partner!
