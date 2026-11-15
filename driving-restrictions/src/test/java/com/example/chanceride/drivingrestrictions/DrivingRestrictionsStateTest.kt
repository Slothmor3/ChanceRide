package com.example.chanceride.drivingrestrictions

import org.junit.Assert.*
import org.junit.Test

class DrivingRestrictionsStateTest {
    private fun state(requiresDo: Boolean, flags: Int) = DrivingRestrictionsState(
        RestrictionConnection.AVAILABLE,
        UxRestrictions(requiresDo, flags, 20, 12, 2)
    )

    @Test fun noConnectionNeverPermitsCategoryEditingEvenWithAnOldUnlockedSnapshot() {
        RestrictionConnection.entries.filter { it != RestrictionConnection.AVAILABLE }.forEach {
            val value = state(false, 0).copy(connection = it)
            assertFalse(value.canSelectCategory)
            assertFalse(value.canShowVoiceTranscription)
        }
        assertFalse(DrivingRestrictionsState(RestrictionConnection.AVAILABLE).canSelectCategory)
    }

    @Test fun categoryPolicyUsesPlatformOptimizationRequirementRatherThanGear() {
        assertTrue(state(false, 0).canSelectCategory)
        assertFalse(state(true, 0).canSelectCategory)
        assertFalse(state(true, UxRestriction.NO_VIDEO).canSelectCategory)
    }

    @Test fun featureFlagsAreAppliedIndependentlyOnlyWhenOptimizationIsRequired() {
        val videoOnly = state(true, UxRestriction.NO_VIDEO)
        assertFalse(videoOnly.allows(UxRestriction.NO_VIDEO))
        assertTrue(videoOnly.allows(UxRestriction.NO_KEYBOARD))
        assertTrue(videoOnly.canShowVoiceTranscription)
        val transcript = state(true, UxRestriction.NO_VOICE_TRANSCRIPTION or UxRestriction.NO_SETUP)
        assertFalse(transcript.canShowVoiceTranscription)
        assertFalse(transcript.allows(UxRestriction.NO_SETUP))
        assertTrue(transcript.allows(UxRestriction.NO_VIDEO))
        assertTrue(state(false, 511).allows(UxRestriction.NO_VIDEO))
    }

    @Test fun platformContentLimitsAreRetainedForConsumers() {
        val snapshot = state(true, UxRestriction.LIMIT_CONTENT or UxRestriction.LIMIT_STRING_LENGTH).restrictions!!
        assertEquals(20, snapshot.maxRestrictedStringLength)
        assertEquals(12, snapshot.maxCumulativeContentItems)
        assertEquals(2, snapshot.maxContentDepth)
    }
}
