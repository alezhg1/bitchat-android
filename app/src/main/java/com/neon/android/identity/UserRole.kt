package com.bitchat.android.identity

enum class UserRole(val displayNameRu: String) {
    ADMIN("Администратор"),
    TEACHER("Преподаватель"),
    STUDENT("Ученик");

    fun canViewMap(): Boolean = this == ADMIN || this == TEACHER

    companion object {
        fun fromString(value: String?): UserRole {
            return entries.find { it.name == value } ?: STUDENT
        }
    }
}
