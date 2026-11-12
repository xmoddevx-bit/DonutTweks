// The version that is currently active in the IDE / for regular Gradle tasks.
// Switch it with the "Set active project to <version>" tasks, e.g.:
//   .\gradlew "Set active project to 1.21.11"
stonecutter active "26.3"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    // Replaces the /*$ mod_version*/ and /*$ minecraft*/ placeholders in Java sources
    swaps["mod_version"] = "\"${properties["mod.version"]}\";"
    swaps["minecraft"] = "\"${current.version}\";"
}
