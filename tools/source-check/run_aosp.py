#!/usr/bin/env python3
"""Offline AOSP Android13 AtomicFile algorithm + production writer on synthetic JVM files; no APK/Android runtime."""
from pathlib import Path
import os,re,subprocess,hashlib,json,sys
project=Path(__file__).resolve().parents[2]
root=Path(os.environ.get('DGU_CHECK_TOOLS',str(project.parents[1]/'.local-tools/dgu-check')))
work=Path(os.environ.get('DGU_CHECK_OUTPUT',str(project/'tools/source-check/output-aosp')))
work.mkdir(parents=True,exist_ok=True);(work/'fixtures').mkdir(exist_ok=True)
aosp=project/'tools/source-check/aosp';original=aosp/'AtomicFile-android13.java';text=original.read_text()
assert hashlib.sha256(original.read_bytes()).hexdigest() == 'fd8e9350b0d12648e9886162e4cbd2fb64311a3a4c9bcf7144ceaaaa145fc8fe', 'Vendored AOSP source differs from pinned input'
# Only annotations and unused SystemClock import are removed. I/O/method bodies remain byte-for-byte unchanged.
text=re.sub(r'import android.annotation.\w+;\n','',text).replace('import android.os.SystemClock;\n','')
for annotation in ['@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)','@SuppressLint("StreamFiles")','@NonNull','@Nullable','@CurrentTimeMillisLong']:text=text.replace(annotation,'')
generated=work/'generated/android/util/AtomicFile.java';generated.parent.mkdir(parents=True,exist_ok=True);generated.write_text(text)
classes=work/'classes';classes.mkdir(exist_ok=True)
java=next(root.glob('jdk-*/bin/java'));javac=java.parent/'javac'
commands=[]
def run(cmd,name,env=None):
 commands.append([str(c) for c in cmd]);result=subprocess.run(list(map(str,cmd)),cwd=project,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 (work/name).write_text(result.stdout);print(result.stdout)
 if result.returncode:sys.exit(result.returncode)
run([javac,'-d',classes,generated,aosp/'FileUtils.java',aosp/'PlatformShim.java',aosp/'IoUtils.java'],'javac.log')
cp=':'.join(map(str,[classes,root/'kotlin-stdlib.jar',root/'annotations.jar']))
compiler=':'.join(map(str,root.glob('*.jar')))
writer=project/'app/src/main/java/com/example/dguactapp/AtomicSnapshotWriter.kt'
run([java,'-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',work/'tests.jar',writer,aosp/'AospRegression.kt'],'compile.log')
run([java,'-cp',str(work/'tests.jar')+':'+cp,'com.example.dguactapp.AospRegressionKt'],'results.txt',env={**os.environ,'DGU_CHECK_FIXTURES':str(work/'fixtures')})
(work/'metadata.json').write_text(json.dumps({'aosp_source':'https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/core/java/android/util/AtomicFile.java','aosp_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'writer_sha256':hashlib.sha256(writer.read_bytes()).hexdigest(),'commands':commands,'android_runtime':False,'host_shims':True},indent=2)+'\n')
