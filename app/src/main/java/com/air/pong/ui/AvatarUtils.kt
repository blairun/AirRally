package com.air.pong.ui

import com.air.pong.R

import android.content.Context

object AvatarUtils {
    private var _avatarResources: List<Int> = emptyList()
    val avatarResources: List<Int>
        get() = _avatarResources

    fun initialize(context: Context) {
        val list = mutableListOf<Int>()
        var i = 1
        while (true) {
            val resId = context.resources.getIdentifier("avatar_$i", "drawable", context.packageName)
            if (resId == 0) break
            list.add(resId)
            i++
        }
        _avatarResources = list
    }
}
