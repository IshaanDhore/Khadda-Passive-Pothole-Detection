// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

allprojects {
    // Redirect build directory outside of OneDrive to prevent "Cannot snapshot ... not a regular file" lock errors
    layout.buildDirectory.set(File(System.getProperty("java.io.tmpdir"), "KhaddaBuild/${project.name}"))
}
