#!/usr/bin/env python3
"""Read source/XML and git only; no app, build system, network or device calls."""
from pathlib import Path
import subprocess,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
base='bf68102710cad2cc2629326814338e01a337a409'
def original(path):return subprocess.check_output(['git','show',base+':'+path],cwd=root,text=True)
app=root/'app/src/main/java/com/example/dguactapp'
build=(root/'app/build.gradle.kts').read_text();old=original('app/build.gradle.kts')
for key in ['namespace','applicationId','compileSdk','minSdk','targetSdk','versionCode','versionName']:
 assert re.search(r'\b'+key+r'\s*=\s*([^\n]+)',build)[1]==re.search(r'\b'+key+r'\s*=\s*([^\n]+)',old)[1],key
assert (root/'app/src/main/AndroidManifest.xml').read_text()==original('app/src/main/AndroidManifest.xml')
for p in (root/'app/src/main/res').rglob('*.xml'):ET.parse(p)
string_names={item.attrib['name'] for p in (root/'app/src/main/res').glob('values/*.xml') for item in ET.parse(p).getroot() if item.tag=='string'}
for p in app.rglob('*.kt'):
 for name in re.findall(r'R\.string\.(\w+)',p.read_text()):assert name in string_names,(p,name)
pdf=(app/'ActPdfGenerator.kt').read_text();oldpdf=original('app/src/main/java/com/example/dguactapp/ActPdfGenerator.kt')
for name in ['executorRequisites','legalSection','transferSubjectSection','transferAcceptanceSection','transferStorageSection']:
 def block(text):return re.search(r'private (?:const )?val '+name+r'\s*=([\s\S]+?)(?=\n    private |\n    fun )',text)[1]
 assert block(pdf)==block(oldpdf),name
for line in oldpdf.splitlines():
 if 'drawParagraph(' in line and any(term in line for term in ['Гарантия','Заказчик подтверждает','претензий','оборудование передано','Оборудование передано']):assert line.strip() in pdf,line
assert '.take(62)' not in pdf
assert not list(root.rglob('*.apk'))
assert subprocess.check_output(['git','branch','--show-current'],cwd=root,text=True).strip()=='feature/dgu-local-reliability-2026-10-23'
print('PASS identity/version/SDK/manifest unchanged; XML and string IDs valid; legal blocks unchanged; no truncated serial or APK; feature branch only')
