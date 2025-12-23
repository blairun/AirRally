package com.air.pong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.air.pong.R
import com.air.pong.core.game.GameMode

/**
 * Defines which game modes a tip applies to.
 */
enum class TipMode {
    ALL,           // All game modes
    CLASSIC,       // Classic 1v1 only
    RALLY_COOP,    // Rally co-op (2 player)
    SOLO_RALLY     // Solo rally (future)
}

/**
 * A scrolling tip with its string resource and applicable modes.
 */
data class ScrollingTip(
    val stringResId: Int,
    val modes: Set<TipMode>
)

/**
 * Provider for scrolling tips filtered by game mode.
 */
object ScrollingTipsProvider {
    
    private val allTips: List<ScrollingTip> = listOf(
        // Safety & Controls (ALL)
        ScrollingTip(R.string.tip_safety_strap, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_safety_area, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_hold_screen, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_yellow_incoming, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_green_swing, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_red_wait, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_volume_up, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_screen_rotation, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_hit_window, setOf(TipMode.ALL)),
        
        // Shot Types (ALL)
        ScrollingTip(R.string.tip_flat_shot, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_lob_shot, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_smash_shot, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_harder_faster, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_soft_more_time, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_lobs_safe, setOf(TipMode.ALL)),
        
        // Shot Types (CLASSIC only - risk mechanics)
        ScrollingTip(R.string.tip_smash_shrinks, setOf(TipMode.CLASSIC)),
        ScrollingTip(R.string.tip_smash_risk, setOf(TipMode.CLASSIC)),
        
        // Classic Mode Rules
        ScrollingTip(R.string.tip_classic_first_11, setOf(TipMode.CLASSIC)),
        ScrollingTip(R.string.tip_classic_serve_switch, setOf(TipMode.CLASSIC)),
        ScrollingTip(R.string.tip_classic_score_miss, setOf(TipMode.CLASSIC)),
        ScrollingTip(R.string.tip_classic_mix_shots, setOf(TipMode.CLASSIC)),
        
        // Rally Mode (Co-op + Solo)
        ScrollingTip(R.string.tip_rally_9_types, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_multi_line, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_3_lines, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_x_clear, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_points_scale, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_extra_life, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        // ScrollingTip(R.string.tip_rally_happy_numbers, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_triangular_numbers, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_vertical_bonus, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_diagonal_bonus, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_window_shrink, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        
        // Rally Co-op Only
        ScrollingTip(R.string.tip_coop_teamwork, setOf(TipMode.RALLY_COOP)),
        ScrollingTip(R.string.tip_coop_lobs_partner, setOf(TipMode.RALLY_COOP)),
        ScrollingTip(R.string.tip_coop_3_lives, setOf(TipMode.RALLY_COOP)),
        
        // Bonus Mechanics (Rally + Solo)
        ScrollingTip(R.string.tip_rally_bonus_spin_mix, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_spin_keep_going, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_spin_infinite, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_copy_streak, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_golden_points, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_golden_lines, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_banana, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_landmine, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_shield_earn, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_rally_bonus_shield_save, setOf(TipMode.RALLY_COOP, TipMode.SOLO_RALLY)),

        // Bonus Mechanics (Co-op Only)
        ScrollingTip(R.string.tip_rally_bonus_copy_cat, setOf(TipMode.RALLY_COOP)),
        
        // Solo Rally Only
        ScrollingTip(R.string.tip_solo_practice, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_personal_best, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_longer_flight, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_wall_bounce, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_rhythm, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_no_waiting, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_all_swings, setOf(TipMode.SOLO_RALLY)),
        ScrollingTip(R.string.tip_solo_warmup, setOf(TipMode.SOLO_RALLY)),

        
        // Fun Facts (ALL)
        ScrollingTip(R.string.tip_fact_ball_speed, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_accelerometer, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_olympic, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_whiff_whaff, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_no_balls, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_spin_rpm, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_longest_rally, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_reaction_time, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_pong_arcade, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_grip_safety, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_ball_weight, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_bluetooth_king, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_gyro_vs_accel, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_freq_hop, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_mems_tech, setOf(TipMode.ALL)),
        ScrollingTip(R.string.tip_fact_packet_speed, setOf(TipMode.ALL))
    )
    
    /**
     * Get list of applicable tip resource IDs for the given game mode.
     */
    fun getTipsForMode(gameMode: GameMode, isSolo: Boolean = false): List<Int> {
        val applicableModes = mutableSetOf(TipMode.ALL)
        
        when (gameMode) {
            GameMode.CLASSIC -> applicableModes.add(TipMode.CLASSIC)
            GameMode.RALLY -> {
                if (isSolo) {
                    applicableModes.add(TipMode.SOLO_RALLY)
                } else {
                    applicableModes.add(TipMode.RALLY_COOP)
                }
            }
            GameMode.SOLO_RALLY -> applicableModes.add(TipMode.SOLO_RALLY)
        }
        
        return allTips
            .filter { tip -> tip.modes.any { it in applicableModes } }
            .map { it.stringResId }
    }
    
    /**
     * Get a random tip string resource ID for the given game mode.
     */
    fun getRandomTipResId(gameMode: GameMode, isSolo: Boolean = false): Int {
        val tips = getTipsForMode(gameMode, isSolo)
        return tips.randomOrNull() ?: R.string.tip_hold_screen
    }
}

/**
 * Composable helper to get a random tip text for the current mode.
 */
@Composable
fun rememberRandomTip(gameMode: GameMode, isSolo: Boolean = false, key: Any = Unit): String {
    val tipResId = androidx.compose.runtime.remember(gameMode, isSolo, key) {
        ScrollingTipsProvider.getRandomTipResId(gameMode, isSolo)
    }
    return stringResource(tipResId)
}
