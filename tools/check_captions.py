"""Run the real sentence/continuation parser and corpus checks without Minecraft."""
import argparse
import base64
import json
import shutil
import subprocess
from pathlib import Path

from check_translations import ROOT, originals, TRANSLATIONS


def check(java_home=None, regressions_only=False):
    javac = str(java_home / 'bin/javac.exe') if java_home else shutil.which('javac')
    java = str(java_home / 'bin/java.exe') if java_home else shutil.which('java')
    assert javac and java, 'Install a JDK or pass --java-home'
    output = ROOT / 'build/caption-tests'
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(output),
                    str(ROOT / 'src/main/java/dev/ieponder/client/CaptionUnits.java'),
                    str(ROOT / 'src/captionTest/java/dev/ieponder/client/CaptionUnitsTest.java')], check=True)
    command = [java, '-Dfile.encoding=UTF-8', '-cp', str(output), 'dev.ieponder.client.CaptionUnitsTest']
    rows = []
    if not regressions_only:
        source = originals()
        for locale in json.loads((TRANSLATIONS / 'languages.json').read_text('utf-8')):
            if locale in ('en_us', 'ja_jp'):
                language = 'en' if locale == 'en_us' else 'ja'
                values = {key: text[language] for key, text in source.items()}
            else:
                values = json.loads((TRANSLATIONS / 'content' / f'{locale}.json').read_text('utf-8'))
            for key, text in values.items():
                if key.startswith('ieponder.ponder.shared.'):
                    rows.append(locale + '\t' + key + '\t' + base64.b64encode(text.encode('utf-8')).decode('ascii'))
        command.append('--audit')
    subprocess.run(command, input='\n'.join(rows), encoding='utf-8', check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--regressions-only', action='store_true')
    args = parser.parse_args()
    check(args.java_home, args.regressions_only)
