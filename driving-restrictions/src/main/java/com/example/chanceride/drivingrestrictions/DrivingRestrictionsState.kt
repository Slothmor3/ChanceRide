package com.example.chanceride.drivingrestrictions

/** Platform flag values, kept Android-independent so UI policy can be unit tested. */
object UxRestriction {
    const val NO_DIALPAD = 1
    const val NO_FILTERING = 2
    const val LIMIT_STRING_LENGTH = 4
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val LIMIT_CONTENT = 32
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
    const val NO_VOICE_TRANSCRIPTION = 256
}

data class UxRestrictions(
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
    val maxRestrictedStringLength: Int,
    val maxCumulativeContentItems: Int,
    val maxContentDepth: Int
) {
    fun isRestricted(flag: Int): Boolean =
        requiresDistractionOptimization && (activeRestrictions and flag) != 0
}

enum class RestrictionConnection { STOPPED, CONNECTING, AVAILABLE, DISCONNECTED, UNSUPPORTED, ERROR }

data class DrivingRestrictionsState(
    val connection: RestrictionConnection = RestrictionConnection.STOPPED,
    val restrictions: UxRestrictions? = null,
    val message: String = "주행 규제 상태 미확인"
) {
    val hasCurrentRestrictions: Boolean
        get() = connection == RestrictionConnection.AVAILABLE && restrictions != null

    /**
     * Conservative product policy for this unreviewed category-setting demo.
     * A simple category button is NOT automatically prohibited by NO_FILTERING
     * (that flag concerns character-entry filtering). An approved DO UI can supply
     * its own policy using the snapshot and applicable content/depth limits.
     */
    val canSelectCategory: Boolean
        get() = hasCurrentRestrictions && restrictions?.requiresDistractionOptimization == false

    val canShowVoiceTranscription: Boolean
        get() = hasCurrentRestrictions &&
            restrictions?.isRestricted(UxRestriction.NO_VOICE_TRANSCRIPTION) == false

    fun allows(flag: Int): Boolean =
        hasCurrentRestrictions && restrictions?.isRestricted(flag) == false
}
