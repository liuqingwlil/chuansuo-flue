"""Verify the standard development and release build outputs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'app/build'
PACKAGE = 'com.chuansuo.app'


def run(*args):
    return subprocess.check_output([str(arg) for arg in args], encoding='utf-8', errors='replace')


def verify(sdk):
    tools = sdk / 'build-tools/36.0.0'
    aapt = tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    signer = tools / 'lib/apksigner.jar'
    apks = []
    lint = {}
    for build_type, label in (('debug', '穿梭开发版'), ('release', '穿梭')):
        directory = BUILD / 'outputs/apk' / build_type
        metadata = json.loads((directory / 'output-metadata.json').read_text(encoding='utf-8'))
        assert len(metadata['elements']) == 1, f'Expected one {build_type} APK'
        apk = directory / metadata['elements'][0]['outputFile']
        badging = run(aapt, 'dump', 'badging', apk)
        manifest = run(aapt, 'dump', 'xmltree', '--file', 'AndroidManifest.xml', apk)
        assert f"package: name='{PACKAGE}'" in badging, apk
        assert f"application-label:'{label}'" in badging, apk
        assert "name='com.chuansuo.app.WelcomeActivity'" in badging, apk
        assert "native-code: 'arm64-v8a'" in badging, apk
        assert f'"{PACKAGE}.fileProvider"' in manifest, apk
        assert 'InstanceActivity' not in manifest, apk
        assert 'com.chuansuo.app.instance' not in manifest, apk
        assert 'com.baidu.lbsapi.API_KEY' in manifest, apk
        for activity in ('LibraryActivity', 'RouteActivity'):
            assert f'{PACKAGE}.{activity}' in manifest, (apk, activity)
        is_debug = build_type == 'debug'
        with ZipFile(apk) as archive:
            dex = [archive.read(name) for name in archive.namelist()
                   if name.startswith('classes') and name.endswith('.dex')]
        for field in (b'setting_map_key', b'setting_log_off'):
            assert any(field in entry for entry in dex) == is_debug, (apk, field)
        if is_debug:
            assert "versionName='" in badging and "-dev'" in badging.splitlines()[0], apk
            run('java', '-jar', signer, 'verify', apk)
        else:
            assert "-dev'" not in badging.splitlines()[0], apk
        apks.append({'type': build_type, 'package': PACKAGE, 'label': label, 'file': apk.name,
                     'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()})
        report = ET.parse(BUILD / f'reports/lint-results-{build_type}.xml').getroot()
        lint[build_type] = {severity: sum(issue.get('severity') == severity for issue in report)
                            for severity in ('Error', 'Warning')}
        assert lint[build_type]['Error'] == 0, lint[build_type]
    tests = failures = errors = 0
    for file in (BUILD / 'test-results/testDebugUnitTest').glob('TEST-*.xml'):
        suite = ET.parse(file).getroot()
        tests += int(suite.get('tests', 0))
        failures += int(suite.get('failures', 0))
        errors += int(suite.get('errors', 0))
    assert tests >= 47 and failures == 0 and errors == 0, (tests, failures, errors)
    return {'tests': tests, 'failures': failures, 'errors': errors, 'lint': lint, 'apks': apks}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--report', type=Path, default=ROOT / 'build/reports/apk-validation.json')
    args = parser.parse_args()
    sdk = args.sdk
    local_properties = ROOT / 'local.properties'
    if not sdk and local_properties.is_file():
        for line in local_properties.read_text(encoding='utf-8').splitlines():
            if line.startswith('sdk.dir='):
                sdk = line.split('=', 1)[1].replace('\\:', ':').replace('\\\\', '\\')
                break
    if not sdk:
        parser.error('Set ANDROID_HOME or pass --sdk')
    result = verify(Path(sdk))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Verified {result['tests']} tests, {len(result['lint'])} lint reports and {len(result['apks'])} APKs.")
