"""Summarizes the last test run: total, failed and errored counts, and each failure's message.

Run after ./gradlew :common:test (its console output is noisy):
    python scripts/test-summary.py
Exits 1 if anything failed, or if the results are older than the sources (the last run produced none, as when
compilation fails), so it can gate other commands.
"""
import glob
import os
import re
import sys

COMMON = os.path.join(os.path.dirname(__file__), '..', 'common')
RESULTS = os.path.join(COMMON, 'build', 'test-results', 'test', '*.xml')
SOURCES = os.path.join(COMMON, 'src')
SUITE = re.compile(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"')
FAILED = re.compile(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<(failure|error) message="([^"]*)"')


def newest_source():
    """The most recently changed file under common/src, as (modified time, path)."""
    newest = (0.0, None)
    for root, _, names in os.walk(SOURCES):
        for name in names:
            path = os.path.join(root, name)
            newest = max(newest, (os.path.getmtime(path), path))
    return newest


def main():
    files = glob.glob(RESULTS)
    if not files:
        print('No test results; run ./gradlew :common:test first')
        return 1
    stale = False
    changed, source = newest_source()
    if changed > max(os.path.getmtime(path) for path in files):
        # Gradle writes every result file of a run, so a source edited since the newest one was not tested
        stale = True
        print('STALE: %s changed after the last test results were written; the last run produced none '
              '(did compilation fail?). Run ./gradlew :common:test without -q to see why.'
              % os.path.relpath(source, COMMON))
    tests = skipped = failures = errors = 0
    for path in files:
        with open(path, encoding='utf-8') as f:
            text = f.read()
        m = SUITE.search(text)
        if m:
            tests += int(m[1])
            skipped += int(m[2])
            failures += int(m[3])
            errors += int(m[4])
        for x in FAILED.finditer(text):
            print('%s %s: %s | %s' % (x[3].upper(), x[2].rsplit('.', 1)[-1], x[1], x[4][:400]))
    print('%d tests, %d failed, %d errors, %d skipped' % (tests, failures, errors, skipped))
    return 1 if failures or errors or stale else 0


if __name__ == '__main__':
    sys.exit(main())
