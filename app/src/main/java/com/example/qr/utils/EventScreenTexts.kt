package com.example.qr.utils

import android.content.Context

/**
 * 이벤트(스캔) 화면에 표시되는 문구 저장소.
 * 웹 모니터링 화면에서 수정하면 SharedPreferences에 저장되고,
 * EventFragment가 변경을 감지해 화면에 바로 반영합니다.
 */
object EventScreenTexts {
    const val PREFS = "event_screen_texts"
    const val KEY_UPDATED_AT = "updated_at"

    const val KEY_LABEL_ORGANIZATION = "labelOrganization"
    const val KEY_LABEL_LICENSE = "labelLicense"
    const val KEY_LABEL_ENTRY_TIME = "labelEntryTime"
    const val KEY_LABEL_EXIT_TIME = "labelExitTime"
    const val KEY_LABEL_DURATION = "labelDuration"
    const val KEY_TEXT_WAITING = "textWaiting"
    const val KEY_STATUS_INSIDE = "statusInside"
    const val KEY_STATUS_EXITED = "statusExited"

    /** 문구 한 개의 최대 길이 */
    const val MAX_LENGTH = 40

    /** 기본 문구 (키 순서 = 웹 화면 표시 순서) */
    val DEFAULTS: Map<String, String> = linkedMapOf(
        KEY_LABEL_ORGANIZATION to "소속",
        KEY_LABEL_LICENSE to "면허 번호",
        KEY_LABEL_ENTRY_TIME to "입장 시간",
        KEY_LABEL_EXIT_TIME to "퇴장 시간",
        KEY_LABEL_DURATION to "체류 시간",
        KEY_TEXT_WAITING to "QR 코드를 스캔해주세요",
        KEY_STATUS_INSIDE to "입장중",
        KEY_STATUS_EXITED to "퇴장완료"
    )

    /** 저장된 문구를 읽습니다. 저장값이 없거나 비어 있으면 기본 문구를 돌려줍니다. */
    fun load(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DEFAULTS.mapValues { (key, default) ->
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
        }
    }

    /**
     * 문구를 저장합니다. 알 수 없는 키는 무시하고,
     * 빈 값은 저장값을 지워 기본 문구로 되돌립니다.
     */
    fun save(context: Context, values: Map<String, String?>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in DEFAULTS.keys) {
            if (!values.containsKey(key)) continue
            val value = values[key]?.trim()?.take(MAX_LENGTH).orEmpty()
            if (value.isEmpty()) editor.remove(key) else editor.putString(key, value)
        }
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        editor.apply()
    }

    /** 행사명/날짜처럼 DB에 있는 값이 바뀌었을 때 화면 갱신만 알립니다. */
    fun notifyChanged(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }
}
