from pathlib import Path
import xml.etree.ElementTree as ET
import re
root = Path(__file__).resolve().parents[1]
required = ['settings.gradle.kts','app/build.gradle.kts','app/src/main/AndroidManifest.xml',
            '.github/workflows/android.yml','README.md']
for file in required: assert (root/file).is_file(), file
for file in root.rglob('*.xml'): ET.parse(file)
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
permissions = {p.attrib[ns+'name'] for p in manifest.findall('uses-permission')}
assert permissions == {'android.permission.CAMERA','android.permission.INTERNET'}
application = manifest.find('application')
assert application.attrib[ns+'allowBackup'] == 'false'
assert application.attrib[ns+'usesCleartextTraffic'] == 'false'
for file in (root/'app/src').rglob('*.kt'):
    text = file.read_text()
    assert 'package app.godeye' in text, file
    assert not re.search(r'sk-[A-Za-z0-9]{20,}', text), file
    assert 'TODO(' not in text, file
client = (root/'app/src/main/java/app/godeye/VisionClient.kt').read_text()
assert '.followRedirects(false)' in client
assert 'invokeOnCancellation' in client
assert 'Log.' not in client
print('PASS: required files, XML parsing, permission allowlist, backup/TLS policy, package names and basic source assertions.')
print('NOT RUN: Kotlin compilation, JUnit, Android lint, device tests, real API tests.')
