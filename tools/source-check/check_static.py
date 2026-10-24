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
manifest_now=ET.fromstring((root/'app/src/main/AndroidManifest.xml').read_text())
manifest_old=ET.fromstring(original('app/src/main/AndroidManifest.xml'))
a='{http://schemas.android.com/apk/res/android}'
features=manifest_now.findall('uses-feature')
if features:
 assert len(features)==1 and features[0].get(a+'name')=='android.hardware.camera' and features[0].get(a+'required')=='false'
 manifest_now.remove(features[0])
for tree in [manifest_now, manifest_old]:
 for node in tree.iter():
  if node.text is not None and not node.text.strip(): node.text=None
  if node.tail is not None and not node.tail.strip(): node.tail=None
assert ET.tostring(manifest_now)==ET.tostring(manifest_old)
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
assert not any(p.endswith(('.apk', '.aab', '.keystore', '.jks')) for p in subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines())
assert subprocess.check_output(['git','branch','--show-current'],cwd=root,text=True).strip()in {'feature/dgu-local-reliability-2026-10-23', 'feature/dgu-tablet-test-build-2026-10-24'}
print('PASS identity/version/SDK/production application settings unchanged; only optional camera feature declared; XML and string IDs valid; legal blocks unchanged; no truncated serial or tracked APK/key; permitted feature branch only')

if (root/'app/src/fieldTest/AndroidManifest.xml').exists():
 ns={'a':'http://schemas.android.com/apk/res/android','t':'http://schemas.android.com/tools'}
 manifest=ET.parse(root/'app/src/fieldTest/AndroidManifest.xml').getroot()
 application=manifest.find('application')
 assert application.get('{'+ns['a']+'}allowBackup')=='false'
 assert application.get('{'+ns['a']+'}fullBackupContent')=='false'
 assert 'applicationIdSuffix = ".test"' in build
 assert 'create("fieldTest")' in build
 assert (root/'app/src/main/res/xml/file_paths.xml').read_text()==original('app/src/main/res/xml/file_paths.xml')
 permissions={p.get('{'+ns['a']+'}name'):p.get('{'+ns['t']+'}node') for p in manifest.findall('uses-permission')}
 for name in ['INTERNET','MANAGE_EXTERNAL_STORAGE','READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE','READ_MEDIA_IMAGES','READ_MEDIA_VIDEO','READ_MEDIA_AUDIO']:
  assert permissions['android.permission.'+name]=='remove'
 assert ET.parse(root/'app/src/fieldTest/res/values/strings.xml').getroot().find('string').text=='ДГУ Ямал — ТЕСТ'
 print('PASS dedicated test suffix/label/backup/network/storage source declarations; production application attributes/provider paths preserved (actual APK must be checked independently)')
