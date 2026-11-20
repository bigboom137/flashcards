package androidx.room

import kotlin.reflect.KClass

// The Android app's Entities.kt is shared with the desktop build, where cards are kept in
// a JSON file instead of Room. These no-op stand-ins let its annotations compile here.

@Target(AnnotationTarget.CLASS)
annotation class Entity(val foreignKeys: Array<ForeignKey> = [], val indices: Array<Index> = [])

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Embedded

annotation class Index(vararg val value: String)

annotation class ForeignKey(
    val entity: KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = NO_ACTION,
) {
    companion object {
        const val NO_ACTION = 1
        const val CASCADE = 5
    }
}
