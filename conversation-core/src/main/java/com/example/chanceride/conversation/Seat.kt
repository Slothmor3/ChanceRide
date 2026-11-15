package com.example.chanceride.conversation

/** Manually selected seats. These labels do not imply automatic speaker identification. */
enum class Seat(val label: String) {
    DRIVER("운전석"),
    PASSENGER("조수석"),
    REAR_DRIVER("운전석 뒤"),
    REAR_PASSENGER("조수석 뒤"),
}
