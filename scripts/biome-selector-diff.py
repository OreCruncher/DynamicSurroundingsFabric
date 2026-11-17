#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric
"""
Checks that the biome rules in biomes.json match the same biomes as they did at a git revision, for a change meant to
only rewrite selectors (such as moving a fragment into a scripts.json definition).

Each rule's biomeSelector, before and after, has its def. functions expanded from that version's scripts.json (biome
section), then is checked two ways:

- proof: every trait, and every other term (a function call, a comparison), is taken as true or false in every
  combination; the two selectors must agree in all of them, so they match the same biomes whatever a biome is, a
  modded one included
- biomes: each biome in a /dsdump biomes file is checked with its traits, id and name; terms the dump doesn't hold
  (temperature, rainfall) are tried both ways. Lists, by rule, the biomes matched before and after when they differ

    python scripts/biome-selector-diff.py                       HEAD against the working tree
    python scripts/biome-selector-diff.py --since HEAD~2 --dump neoforge/run/config/dsurround/dumps/biomes.txt

Rules are paired by position, so the change must not add, remove or reorder rules. Exits 1 if any rule differs.
"""
import argparse
import itertools
import json
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIGS = 'common/src/main/resources/assets/dsurround/dsconfigs/'
COMMENT = re.compile(r'(?m)^\s*//.*$')
TOKEN = re.compile(r"\s*(?:(\|\||&&|==|!=|<=|>=|<|>|!|\(|\)|,)|('[^']*')|(@?[A-Za-z_][A-Za-z0-9_.:/]*)|(-?\d+(?:\.\d+)?))")


def read(rev, path):
    if rev is None:
        with open(os.path.join(REPO, path), encoding='utf-8') as f:
            return f.read()
    result = subprocess.run(['git', 'show', '%s:%s' % (rev, path)], cwd=REPO, capture_output=True, text=True, encoding='utf-8')
    return result.stdout if result.returncode == 0 else None


def version(rev):
    rules = json.loads(COMMENT.sub('', read(rev, CONFIGS + 'biomes.json')))
    scripts = read(rev, CONFIGS + 'scripts.json')
    biome = {}
    if scripts:
        for name, text in json.loads(COMMENT.sub('', scripts)).get('biome', {}).items():
            if not name.startswith('_'):
                biome['def.' + name.lower()] = text
    return [r.get('biomeSelector', '') for r in rules], biome


def expand(text, definitions, depth=0):
    if depth > 20:
        raise ValueError('definitions nest too deep: ' + text)

    def sub(m):
        key = m.group(1).lower()
        if key not in definitions:
            raise ValueError('%s is not a biome definition' % m.group(1))
        return '(' + expand(definitions[key], definitions, depth + 1) + ')'
    return re.sub(r'(def\.[A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)', sub, text)


def tokens(text):
    out, pos = [], 0
    text = text.strip()
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m or m.end() == pos:
            raise ValueError('cannot read %r at %d' % (text, pos))
        out.append(next(g for g in m.groups() if g is not None))
        pos = m.end()
    return out


class Parser:
    """Boolean structure over atoms: a trait, or any other term (a call with its arguments, a comparison) as text"""

    def __init__(self, text):
        self.t = tokens(text)
        self.i = 0

    def peek(self):
        return self.t[self.i] if self.i < len(self.t) else None

    def take(self):
        self.i += 1
        return self.t[self.i - 1]

    def parse(self):
        node = self.or_()
        if self.peek() is not None:
            raise ValueError('unexpected %r' % self.peek())
        return node

    def or_(self):
        node = self.and_()
        while self.peek() == '||':
            self.take()
            node = ('or', node, self.and_())
        return node

    def and_(self):
        node = self.unary()
        while self.peek() == '&&':
            self.take()
            node = ('and', node, self.unary())
        return node

    def unary(self):
        if self.peek() == '!':
            self.take()
            return ('not', self.unary())
        if self.peek() == '(':
            self.take()
            node = self.or_()
            if self.take() != ')':
                raise ValueError('missing )')
            return node
        return self.atom()

    def term(self):
        start = self.i
        word = self.take()
        if self.peek() == '(':
            depth = 0
            while True:
                tok = self.take()
                depth += tok == '('
                depth -= tok == ')'
                if depth == 0:
                    break
        return ' '.join(self.t[start:self.i])

    def atom(self):
        text = self.term()
        if self.peek() in ('==', '!=', '<', '>', '<=', '>='):
            text += ' ' + self.take() + ' ' + self.term()
        return ('atom', text)


def atoms(node, found):
    if node[0] == 'atom':
        found.add(node[1])
    else:
        for child in node[1:]:
            atoms(child, found)
    return found


def evaluate(node, values):
    kind = node[0]
    if kind == 'atom':
        return values[node[1]]
    if kind == 'not':
        return not evaluate(node[1], values)
    if kind == 'and':
        return evaluate(node[1], values) and evaluate(node[2], values)
    return evaluate(node[1], values) or evaluate(node[2], values)


def biomes(dump):
    found = []
    with open(dump, encoding='utf-8') as f:
        text = f.read()
    for block in text.split('\nBiome [')[0:]:
        m = re.match(r'(?:Biome \[)?(.*?)/([^\]]+)\]', block)
        t = re.search(r'^Traits: (.*)$', block, re.M)
        if not m or not t:
            continue
        traits = set(re.findall(r'[A-Z][A-Z_]+', re.sub(r'\([^)]*\)', '', t.group(1))))
        found.append((m.group(1), m.group(2), traits))
    return found


def known(atom, name, ident, traits):
    """A term's value for a biome from the dump, or None if the dump doesn't hold it"""
    if re.fullmatch(r'[A-Z][A-Z_]*', atom):
        return atom in traits
    if atom.startswith('@'):
        return None
    m = re.fullmatch(r"biome\.getId \( \) == '([^']*)'", atom)
    if m:
        return ident == m.group(1)
    m = re.fullmatch(r"biome\.getName \( \) == '([^']*)'", atom)
    if m:
        return name == m.group(1)
    m = re.fullmatch(r"lib\.oneof \( biome\.getId \( \) , (.*) \)", atom)
    if m:
        return ident in re.findall(r"'([^']*)'", m.group(1))
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--since', default='HEAD', help='git revision to compare with (default HEAD)')
    parser.add_argument('--dump', default=None, help='a /dsdump biomes file (default: the newest found)')
    args = parser.parse_args()

    dump = args.dump
    if dump is None:
        candidates = [os.path.join(root, 'biomes.txt') for base in ('fabric', 'neoforge')
                      for root, _, files in os.walk(os.path.join(REPO, base)) if 'biomes.txt' in files and 'dumps' in root]
        dump = max(candidates, key=os.path.getmtime) if candidates else None
    found = biomes(dump) if dump else []
    if not found:
        print('No biome dump found: the proof alone is run', file=sys.stderr)

    before, before_defs = version(args.since)
    after, after_defs = version(None)
    if len(before) != len(after):
        sys.exit('The rule count changed (%d to %d): rules are paired by position' % (len(before), len(after)))

    differing, changed = 0, 0
    for i, (old, new) in enumerate(zip(before, after)):
        if old == new and before_defs == after_defs:
            continue
        a = Parser(expand(old, before_defs)).parse()
        b = Parser(expand(new, after_defs)).parse()
        terms = sorted(atoms(a, set()) | atoms(b, set()))
        if old != new:
            changed += 1
        proof = None
        if len(terms) <= 22:
            proof = all(evaluate(a, dict(zip(terms, combo))) == evaluate(b, dict(zip(terms, combo)))
                        for combo in itertools.product((False, True), repeat=len(terms)))
        lost, gained = [], []
        for name, ident, traits in found:
            fixed = {t: known(t, name, ident, traits) for t in terms}
            open_terms = [t for t in terms if fixed[t] is None]
            for combo in itertools.product((False, True), repeat=len(open_terms)):
                values = dict(fixed)
                values.update(zip(open_terms, combo))
                was, now = evaluate(a, values), evaluate(b, values)
                if was != now:
                    (lost if was else gained).append(ident)
                    break
        if proof is False or lost or gained:
            differing += 1
            print('rule %d DIFFERS' % i)
            print('    before: ' + old)
            print('    after:  ' + new)
            if proof is False:
                print('    not the same for every combination of terms')
            if lost:
                print('    no longer matches: ' + ', '.join(lost))
            if gained:
                print('    now matches: ' + ', '.join(gained))
        elif old != new:
            print('rule %d same%s: %s' % (i, '' if proof else ' (too many terms to prove; biomes agree)', new))
    print('%d rules rewritten, %d differ; %d biomes from %s' % (
        changed, differing, len(found), os.path.relpath(dump, REPO).replace(os.sep, '/') if dump else 'no dump'))
    sys.exit(1 if differing else 0)


if __name__ == '__main__':
    main()
