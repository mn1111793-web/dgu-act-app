#!/usr/bin/env python3
"""Isolated Kotlin/JVM source check with Android API jar and synthetic R. No Gradle or APK."""
from pathlib import Path
import re,subprocess,json,os,sys,hashlib
project=Path(__file__).resolve().parents[2]
root=Path(os.environ.get('DGU_CHECK_TOOLS',str(project.parents[1]/'.local-tools/dgu-check')))
work=Path(os.environ.get('DGU_CHECK_OUTPUT',str(project/'tools/source-check/output')))
work.mkdir(parents=True,exist_ok=True)
files=list((project/'app/src/main/java').rglob('*.kt'))
resources=sorted(set(re.findall(r'R\.string\.(\w+)', '\n'.join(p.read_text() for p in files))))
(work/'R.kt').write_text('package com.example.dguactapp\nobject R { object string {\n'+'\n'.join(f'const val {s} = {n+1}' for n,s in enumerate(resources))+'\n}}')
groups={}
for p in (root/'deps').glob('*.jar'):
 m=re.match(r'(.+)-(\d[^/]*)\.jar$',p.name)
 if not m:continue
 key,version=m.groups()
 parts=tuple(int(x) for x in re.findall(r'\d+',version))
 if key not in groups or parts>groups[key][0]:groups[key]=(parts,p)
deps=[p for _,p in groups.values() if 'compose.compiler.compiler-' not in p.name]
cp=':'.join(map(str,[root/'android.jar',root/'kotlin-stdlib.jar',root/'annotations.jar']+deps))
compiler=':'.join(map(str,root.glob('*.jar')))
java=next(root.glob('jdk-*/bin/java'))
plugin=root/'deps/androidx.compose.compiler.compiler-1.5.14.jar'
cmd=[str(java),'-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-Xplugin='+str(plugin),'-d',str(work/'source-check.jar')]+list(map(str,files))+[str(work/'R.kt')]
(work/'compile-classpath.txt').write_text(cp)
hashes={str(p.relative_to(project)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
result=subprocess.run(cmd,cwd=project,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(work/'source-compile.log').write_text(result.stdout)
unchanged=all(hashlib.sha256((project/path).read_bytes()).hexdigest()==digest for path,digest in hashes.items())
(work/'source-inputs-sha256.json').write_text(json.dumps(hashes,indent=2))
print(result.stdout[-22000:]);print('COMPILER EXIT',result.returncode,'INPUTS UNCHANGED',unchanged)
if not unchanged:sys.exit('Sources changed during check; repeat after edits finish.')

sys.exit(result.returncode)
