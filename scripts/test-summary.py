"""Summarizes the last test run: total, failed and errored counts, and each failure's message.

Run after ./gradlew :common:test (its console output is noisy):
    python scripts/test-summary.py
Exits 1 if anything failed, so it can gate other commands.
"""
import glob
import os
import re
import sys

RESULTS = os.path.join(os.path.dirname(__file__), '..', 'common', 'build', 'test-results', 'test', '*.xml')
SUITE = re.compile(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"')
FAILED = re.compile(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<(failure|error) message="([^"]*)"')


def main():
    files = glob.glob(RESULTS)
    if not files:
        print('No test results; run ./gradlew :common:test first')
        return 1
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
    return 1 if failures or errors else 0


if __name__ == '__main__':
    sys.exit(main())
