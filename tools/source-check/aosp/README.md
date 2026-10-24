# AtomicFile: Android 13 source on a host JVM

Pinned official source (Android tag android-13.0.0_r1):
https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/core/java/android/util/AtomicFile.java

SHA256: `fd8e9350b0d12648e9886162e4cbd2fb64311a3a4c9bcf7144ceaaaa145fc8fe`. Apache 2.0; original header and LICENSE.txt retained.

run_aosp.py has no network operations. It copies this input to an ignored output directory,
removes only annotations and their imports plus unused SystemClock import, then uses javac.
FileUtils provides host JDK fd.sync, with Android setPermissions as a no-op; Log, commit
telemetry and IoUtils are host shims. AtomicFile method/I/O/rename bodies are unchanged.
The Kotlin harness runs the production AtomicSnapshotWriter against that algorithm.

This is NOT Android runtime, a tablet filesystem, genuine ENOSPC, hardware-power-loss
or the manufacturer's exact Android build. Partial write and post-finish faults are
explicitly injected. The actual host rename-refusal case uses a nonempty fixture directory.
Never place these shims in app/src or a device APK.
