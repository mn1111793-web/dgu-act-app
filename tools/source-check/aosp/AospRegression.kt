package com.example.dguactapp

import android.util.AtomicFile
import java.io.*
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory(File(requireNotNull(System.getenv("DGU_CHECK_FIXTURES"))).toPath(), "aosp-").toFile()
    var count = 0
    fun case(name: String, body: (File) -> Unit) { val base = File(root, "case-${count+1}/snapshot").apply { parentFile.mkdirs() }; body(base); count++; println("PASS AOSP $name") }
    case("legacy .bak is restored, stale .new removed by actual openRead") { base ->
        File(base.path+".bak").writeText("old"); File(base.path+".new").writeText("partial")
        check(AtomicFile(base).openRead().use { it.readBytes().decodeToString() } == "old")
        check(!File(base.path+".bak").exists() && !File(base.path+".new").exists())
    }
    case("interrupted overwrite before finish retains committed base") { base ->
        base.writeText("old"); AtomicFile(base).startWrite().use { it.write("partial".toByteArray()) }
        check(AtomicFile(base).openRead().use { it.readBytes().decodeToString() } == "old")
    }
    case("interrupted first write has no readable base and pending bytes survive") { base ->
        AtomicFile(base).startWrite().use { it.write("partial".toByteArray()) }
        check(runCatching { AtomicFile(base).openRead() }.isFailure)
        check(File(base.path+".new").readText()=="partial")
    }
    case("failWrite after successful finish does NOT roll back committed data") { base ->
        base.writeText("old"); val atomic = AtomicFile(base); val stream = atomic.startWrite()
        stream.write("new".toByteArray()); atomic.finishWrite(stream); atomic.failWrite(stream)
        check(base.readText()=="new")
    }
    case("actual rename failure is logged by void finishWrite, not thrown") { base ->
        base.mkdirs(); File(base,"keep").writeText("preserved")
        val atomic = AtomicFile(base); val output = atomic.startWrite(); output.write("new".toByteArray())
        atomic.finishWrite(output)
        check(base.isDirectory && File(base,"keep").readText()=="preserved" && File(base.path+".new").exists())
    }
    case("production writer reports post-finish read failure as uncertain; retry same payload") { base ->
        base.writeText("old"); var rollbacks = 0
        val atomic = object: AtomicFile(base) { override fun failWrite(output: FileOutputStream?) { rollbacks++; super.failWrite(output) } }
        val result = runCatching { AtomicSnapshotWriter(atomic) { throw IOException("injected verification read denial") }.write("new".toByteArray()) }
        check(result.exceptionOrNull() is SnapshotCommitUncertainException && base.readText()=="new" && rollbacks==0)
        AtomicSnapshotWriter(atomic).write("new".toByteArray()); check(base.readText()=="new")
    }
    case("production writer handles exception after real rename without false rollback") { base ->
        base.writeText("old"); var rollbacks = 0
        val atomic = object: AtomicFile(base) {
            override fun finishWrite(output: FileOutputStream?) { super.finishWrite(output); throw IOException("injected after rename") }
            override fun failWrite(output: FileOutputStream?) { rollbacks++; super.failWrite(output) }
        }
        check(runCatching { AtomicSnapshotWriter(atomic).write("new".toByteArray()) }.exceptionOrNull() is SnapshotCommitUncertainException)
        check(base.readText()=="new" && rollbacks==0)
    }
    case("production writer detects silent noncommit with pending file") { base ->
        base.writeText("old"); var rollbacks = 0
        val atomic = object: AtomicFile(base) {
            override fun finishWrite(output: FileOutputStream?) { output?.close() /* injected rename refusal, matching void API */ }
            override fun failWrite(output: FileOutputStream?) { rollbacks++; super.failWrite(output) }
        }
        check(runCatching { AtomicSnapshotWriter(atomic).write("new".toByteArray()) }.exceptionOrNull() is SnapshotCommitUncertainException)
        check(base.readText()=="old" && File(base.path+".new").readText()=="new" && rollbacks==0)
        check(AtomicFile(base).openRead().use { it.readBytes().decodeToString() }=="old")
        AtomicSnapshotWriter(AtomicFile(base)).write("new".toByteArray()); check(base.readText()=="new")
    }
    case("production writer partial write failure cleans staging before finish, preserves old base") { base ->
        base.writeText("old")
        val atomic = object: AtomicFile(base) {
            override fun startWrite(): FileOutputStream = object: FileOutputStream(File(base.path+".new")) {
                override fun write(bytes: ByteArray) { super.write(bytes,0,1); throw IOException("injected ENOSPC") }
            }
        }
        val result = runCatching { AtomicSnapshotWriter(atomic).write("new".toByteArray()) }
        check(result.isFailure && result.exceptionOrNull() !is SnapshotCommitUncertainException)
        check(base.readText()=="old" && !File(base.path+".new").exists())
    }
    println("ALL $count AOSP HOST CASES PASSED; Android13 source with host shims, NOT Android device/runtime")
}
