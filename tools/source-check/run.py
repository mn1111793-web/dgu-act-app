#!/usr/bin/env python3
"""Source-only regression runner. No Gradle, APK packaging, device, network or user-data access.
Tool jars must already be available in DGU_CHECK_TOOLS (see README).
"""
from pathlib import Path
import os, subprocess, re, sys, json, hashlib
project=Path(__file__).resolve().parents[2]
root=Path(os.environ.get('DGU_CHECK_TOOLS',str(project.parents[1]/'.local-tools/dgu-check')))
java=next(root.glob('jdk-*/bin/java'),None)
if java is None: sys.exit('Portable JDK missing; no dependencies are installed by this runner.')
work=Path(os.environ.get('DGU_CHECK_OUTPUT',str(project/'tools/source-check/output')))
work.mkdir(parents=True,exist_ok=True)
(work/"fixtures").mkdir(exist_ok=True)
groups={}
for p in (root/'deps').glob('*.jar'):
 m=re.match(r'(.+)-(\d[^/]*)\.jar$',p.name)
 if m:
  k,v=m.groups();parts=tuple(map(int,re.findall(r'\d+',v)))
  if k not in groups or parts>groups[k][0]:groups[k]=(parts,p)
cp=':'.join(map(str,[root/'kotlin-stdlib.jar',root/'annotations.jar',root/'json.jar',root/'xpp3.jar']+
    [p for _,p in groups.values() if 'compose.compiler.compiler-' not in p.name]))
compiler=':'.join(map(str,root.glob('*.jar')))
source=project/'app/src/main/java/com/example/dguactapp'
files=[source/(s+'.kt') for s in ['ActStorage','ActMetadata','DiagnosticChecklist','SignatureData','LocalRepository','AtomicSnapshotWriter','LocalRules','DraftCodec']]
files+=list((project/'tools/source-check/stubs').glob('*.kt'))+[project/'tools/source-check/Regression.kt', project/'tools/source-check/ReviewRegression.kt']
cmd=[str(java),'-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',str(work/'tests.jar')]+list(map(str,files))
hashes={str(p.relative_to(project)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
commands=[cmd]
result=subprocess.run(cmd,cwd=project,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(work/'compile.log').write_text(result.stdout)
print(result.stdout)
if result.returncode:sys.exit(result.returncode)
programs=['ReviewRegressionKt'] if '--review' in sys.argv else (['RegressionKt','ReviewRegressionKt'] if '--all' in sys.argv else ['RegressionKt'])
for program in programs:
    command=[str(java),'-cp',str(work/'tests.jar')+':'+cp,'com.example.dguactapp.'+program]
    commands.append(command)
    result=subprocess.run(command,env={**os.environ,'DGU_CHECK_FIXTURES':str(work/'fixtures')},cwd=project,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (work/('review-results.txt' if program=='ReviewRegressionKt' else 'results.txt')).write_text(result.stdout)
    print(result.stdout)
    if result.returncode:sys.exit(result.returncode)
unchanged=all(hashlib.sha256((project/p).read_bytes()).hexdigest()==h for p,h in hashes.items())
(work/'regression-metadata.json').write_text(json.dumps({'commands':commands,'inputs':hashes,'inputs_unchanged':unchanged,'programs':programs,'android_runtime':False,'platform_fault_model':True},indent=2)+'\n')
if not unchanged:sys.exit('Inputs changed during regressions; repeat after edits finish.')
