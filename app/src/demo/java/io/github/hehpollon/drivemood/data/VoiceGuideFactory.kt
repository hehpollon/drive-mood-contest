package io.github.hehpollon.drivemood.data

import android.content.Context

fun createVoiceGuide(context: Context): VoiceGuide = AndroidVoiceGuide(context)
