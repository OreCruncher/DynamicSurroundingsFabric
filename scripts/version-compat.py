#!/usr/bin/env python3
"""Checks whether the compiled mod still links against another Minecraft version's game jar.

Everything the mod's class files use from the game (classes, fields, methods, overridden methods) and everything its
mixins name (targets, injected methods, @At call sites, shadows, accessors, invokers) is resolved against a baseline
jar (the version the mod is built for) and a candidate jar. Only what resolves in the baseline and not in the
candidate is reported, so the checker's own blind spots cancel out.

    python scripts/version-compat.py BASELINE.jar CANDIDATE.jar [CLASSES_DIR...]

CLASSES_DIR defaults to the common, fabric and neoforge compile outputs (run compileJava first). The jars are
Mojang's client jars (unobfuscated from 26.1 on), e.g. ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar and
the candidate's client.jar from its version manifest. It doesn't see behaviour changes: a method that still exists but
does something else, an injection point that matches a different call than before, or changed game data.
"""
import os
import re
import struct
import sys
import zipfile

# ---------------------------------------------------------------- class file parsing

OP_LEN = {}
for op in range(0x00, 0x10): OP_LEN[op] = 1
OP_LEN.update({0x10: 2, 0x11: 3, 0x12: 2, 0x13: 3, 0x14: 3})
for op in range(0x15, 0x1a): OP_LEN[op] = 2
for op in range(0x1a, 0x36): OP_LEN[op] = 1
for op in range(0x36, 0x3b): OP_LEN[op] = 2
for op in range(0x3b, 0x84): OP_LEN[op] = 1
OP_LEN[0x84] = 3
for op in range(0x85, 0x99): OP_LEN[op] = 1
for op in range(0x99, 0xa9): OP_LEN[op] = 3
OP_LEN[0xa9] = 2
for op in range(0xac, 0xb2): OP_LEN[op] = 1
for op in range(0xb2, 0xb9): OP_LEN[op] = 3
OP_LEN.update({0xb9: 5, 0xba: 5, 0xbb: 3, 0xbc: 2, 0xbd: 3, 0xbe: 1, 0xbf: 1, 0xc0: 3, 0xc1: 3, 0xc2: 1, 0xc3: 1,
               0xc5: 4, 0xc6: 3, 0xc7: 3, 0xc8: 5, 0xc9: 5})
CP_OPS = {0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xbb, 0xbd, 0xc0, 0xc1, 0xc5}


class ClassFile:
    def __init__(self, data, want_code=False):
        self.cp = [None]
        self._data = data
        p = 10
        count = struct.unpack_from('>H', data, 8)[0]
        i = 1
        while i < count:
            tag = data[p]
            if tag == 1:
                n = struct.unpack_from('>H', data, p + 1)[0]
                self.cp.append(('utf8', data[p + 3:p + 3 + n].decode('utf-8', 'replace')))
                p += 3 + n
            elif tag in (3, 4):
                self.cp.append(('num', None)); p += 5
            elif tag in (5, 6):
                self.cp.extend([('num', None), None]); p += 9; i += 1
            elif tag in (7, 8, 16, 19, 20):
                self.cp.append((tag, struct.unpack_from('>H', data, p + 1)[0])); p += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                self.cp.append((tag,) + struct.unpack_from('>HH', data, p + 1)); p += 5
            elif tag == 15:
                self.cp.append((tag, data[p + 1], struct.unpack_from('>H', data, p + 2)[0])); p += 4
            else:
                raise ValueError('bad constant tag %d' % tag)
            i += 1
        self.access, this, sup, n = struct.unpack_from('>HHHH', data, p)
        p += 8
        self.name = self.cls(this)
        self.super = self.cls(sup) if sup else None
        self.interfaces = [self.cls(struct.unpack_from('>H', data, p + 2 * k)[0]) for k in range(n)]
        p += 2 * n
        self.fields, p = self._members(p, want_code)
        self.methods, p = self._members(p, want_code)
        self.annotations, p = self._attrs(p, want_code)

    def utf(self, i):
        return self.cp[i][1]

    def cls(self, i):
        return self.utf(self.cp[i][1])

    def member_ref(self, i):
        tag, ci, nti = self.cp[i]
        _, ni, di = self.cp[nti]
        return self.cls(ci), self.utf(ni), self.utf(di), tag

    def _members(self, p, want_code):
        n = struct.unpack_from('>H', self._data, p)[0]
        p += 2
        out = []
        for _ in range(n):
            acc, ni, di = struct.unpack_from('>HHH', self._data, p)
            m = {'access': acc, 'name': self.utf(ni), 'desc': self.utf(di)}
            m['annotations'], p = self._attrs(p + 6, want_code, m)
            out.append(m)
        return out, p

    def _attrs(self, p, want_code, member=None):
        n = struct.unpack_from('>H', self._data, p)[0]
        p += 2
        anns = []
        for _ in range(n):
            ni, ln = struct.unpack_from('>HI', self._data, p)
            name = self.utf(ni)
            body = p + 6
            if name in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                k = struct.unpack_from('>H', self._data, body)[0]
                q = body + 2
                for _ in range(k):
                    a, q = self._annotation(q)
                    anns.append(a)
            elif name == 'Code' and member is not None and want_code:
                clen = struct.unpack_from('>I', self._data, body + 4)[0]
                member['refs'] = self._code_refs(self._data[body + 8:body + 8 + clen])
            p = body + ln
        return anns, p

    def _annotation(self, q):
        ti, k = struct.unpack_from('>HH', self._data, q)
        q += 4
        vals = {}
        for _ in range(k):
            ni = struct.unpack_from('>H', self._data, q)[0]
            vals[self.utf(ni)], q = self._element(q + 2)
        return (self.utf(ti), vals), q

    def _element(self, q):
        tag = chr(self._data[q])
        if tag in 'BCDFIJSZ':
            return None, q + 3
        if tag == 's':
            return self.utf(struct.unpack_from('>H', self._data, q + 1)[0]), q + 3
        if tag == 'e':
            return self.utf(struct.unpack_from('>H', self._data, q + 3)[0]), q + 5
        if tag == 'c':
            return ('class', self.utf(struct.unpack_from('>H', self._data, q + 1)[0])), q + 3
        if tag == '@':
            return self._annotation(q + 1)
        if tag == '[':
            k = struct.unpack_from('>H', self._data, q + 1)[0]
            q += 3
            out = []
            for _ in range(k):
                v, q = self._element(q)
                out.append(v)
            return out, q
        raise ValueError('bad element tag ' + tag)

    def _code_refs(self, code):
        refs = set()
        p = 0
        while p < len(code):
            op = code[p]
            if op in CP_OPS:
                i = struct.unpack_from('>H', code, p + 1)[0]
                if self.cp[i][0] == 7:
                    refs.add(('class', self.cls(i)))
                else:
                    owner, name, desc, tag = self.member_ref(i)
                    refs.add(('field' if tag == 9 else 'method', owner, name, desc))
            if op == 0xaa:
                q = p + 1 + (-(p + 1) % 4)
                lo, hi = struct.unpack_from('>ii', code, q + 4)
                p = q + 12 + 4 * (hi - lo + 1)
            elif op == 0xab:
                q = p + 1 + (-(p + 1) % 4)
                npairs = struct.unpack_from('>i', code, q + 4)[0]
                p = q + 8 + 8 * npairs
            elif op == 0xc4:
                p += 6 if code[p + 1] == 0x84 else 4
            else:
                p += OP_LEN[op]
        return refs


# ---------------------------------------------------------------- game jar

class GameJar:
    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.names = {n[:-6] for n in self.zip.namelist() if n.endswith('.class')}
        self._cache = {}

    def get(self, name, code=False):
        if name not in self.names:
            return None
        key = (name, code)
        if key not in self._cache:
            self._cache[key] = ClassFile(self.zip.read(name + '.class'), code)
        return self._cache[key]

    def ancestors(self, name):
        """The class and its supertypes, nearest first; None marks a supertype outside the jar."""
        seen, out, todo = set(), [], [name]
        while todo:
            n = todo.pop(0)
            if n in seen:
                continue
            seen.add(n)
            c = self.get(n)
            if c is None:
                out.append(None)
                continue
            out.append(c)
            todo += ([c.super] if c.super else []) + c.interfaces
        return out

    def resolve(self, kind, owner, name, desc):
        """True / False, or None when the search leaves the jar without finding it (a JDK or library supertype)."""
        external = False
        for c in self.ancestors(owner):
            if c is None:
                external = True
                continue
            for m in (c.fields if kind == 'field' else c.methods):
                if m['name'] == name and (desc is None or m['desc'] == desc):
                    return True
        return None if external else False


# ---------------------------------------------------------------- mod classes

def mod_classes(dirs):
    for d in dirs:
        for root, _, files in os.walk(d):
            for f in files:
                if f.endswith('.class'):
                    path = os.path.join(root, f)
                    with open(path, 'rb') as fh:
                        yield ClassFile(fh.read(), True)


DESC_CLASS = re.compile(r'L([^;<]+);')
MIXIN = 'Lorg/spongepowered/asm/mixin/'
INJECTORS = ('Lorg/spongepowered/asm/mixin/injection/', 'Lcom/llamalad7/mixinextras/injector/')


def references(cf):
    """(kind, owner, name, desc, where) for everything the class file uses."""
    out = set()
    for i, e in enumerate(cf.cp):
        if e and e[0] in (9, 10, 11):
            owner, name, desc, tag = cf.member_ref(i)
            out.add(('field' if tag == 9 else 'method', owner, name, desc))
        elif e and e[0] == 7:
            out.add(('class', cf.cls(i).lstrip('['), None, None))
        elif e and e[0] == 'utf8':
            for c in DESC_CLASS.findall(e[1]):
                out.add(('class', c, None, None))
    return out


def ann_list(v):
    return v if isinstance(v, list) else ([] if v is None else [v])


def strip_class(s):
    s = s.replace('.', '/')
    return s[1:-1] if s.startswith('L') and s.endswith(';') else s


def selector_matches(sel, m):
    """A mixin method selector ("name", "name(desc)", "Lowner;name(desc)", wildcards) against a method."""
    if sel.startswith('L') and ';' in sel:
        sel = sel.split(';', 1)[1]
    name, _, desc = sel.partition('(')
    desc = '(' + desc if desc else None
    if ':' in name:
        name, desc = name.split(':', 1)
    pattern = '^' + re.escape(name).replace(r'\*', '.*') + '$'
    if not re.match(pattern, m['name']):
        return False
    if desc is None:
        return True
    return m['desc'].startswith(desc) if desc.endswith(')') else m['desc'] == desc


def at_ref(at):
    """An @At's (value, target) as the code reference it must find, or None for points with no target."""
    value = at.get('value')
    target = at.get('target')
    if isinstance(target, tuple):  # @Desc
        return None
    if not target or value not in ('INVOKE', 'INVOKE_ASSIGN', 'INVOKE_STRING', 'FIELD', 'NEW'):
        return None
    if value == 'NEW':
        return ('class', strip_class(target.split('(')[0]))
    owner, _, rest = target[1:].partition(';')
    if ':' in rest:
        name, desc = rest.split(':', 1)
        return ('field', owner, name, desc)
    name, _, desc = rest.partition('(')
    return ('method', owner, name, '(' + desc)


def check_mixin(cf, jar):
    """Problems with one mixin class against one jar, as a set of strings."""
    probs = set()
    mixin = next((a for a in cf.annotations if a[0] == MIXIN + 'Mixin;'), None)
    if mixin is None:
        return probs
    targets = [v[1][1:-1] for v in ann_list(mixin[1].get('value')) if isinstance(v, tuple)]
    targets += [strip_class(t) for t in ann_list(mixin[1].get('targets'))]
    present = []
    for t in targets:
        if jar.get(t) is None:
            probs.add('mixin target class %s missing' % t)
        else:
            present.append(t)
    for m in cf.methods + cf.fields:
        is_field = m in cf.fields
        for ann, vals in m['annotations']:
            if ann.startswith(INJECTORS):
                ats = []
                for key in ('at', 'from', 'to'):
                    ats += [a[1] for a in ann_list(vals.get(key)) if isinstance(a, tuple)]
                for sl in ann_list(vals.get('slice')):
                    if isinstance(sl, tuple):
                        ats += [a[1] for k in ('from', 'to') for a in ann_list(sl[1].get(k)) if isinstance(a, tuple)]
                for sel in ann_list(vals.get('method')):
                    if not isinstance(sel, str):
                        continue
                    for t in present:
                        hits = [tm for tm in jar.get(t, True).methods if selector_matches(sel, tm)]
                        if not hits:
                            probs.add('%s: injected method %s not in %s' % (m['name'], sel, t))
                            continue
                        for at in ats:
                            ref = at_ref(at)
                            if ref and not any(ref in tm.get('refs', ()) for tm in hits):
                                probs.add('%s: %s in %s no longer calls/uses %s' % (m['name'], sel, t, at.get('target')))
            elif ann == MIXIN + 'Shadow;':
                prefix = vals.get('prefix', 'shadow$')
                name = m['name'][len(prefix):] if m['name'].startswith(prefix) else m['name']
                kind = 'field' if is_field else 'method'
                for t in present:
                    if jar.resolve(kind, t, name, m['desc']) is False:
                        probs.add('@Shadow %s %s%s not in %s' % (kind, name, m['desc'], t))
            elif ann == MIXIN + 'Overwrite;':
                for t in present:
                    if jar.resolve('method', t, m['name'], m['desc']) is False:
                        probs.add('@Overwrite %s%s not in %s' % (m['name'], m['desc'], t))
            elif ann in (MIXIN + 'gen/Accessor;', MIXIN + 'gen/Invoker;'):
                name = vals.get('value')
                if not name:
                    name = re.sub(r'^(get|set|is|call|invoke)', '', m['name'])
                    name = name[:1].lower() + name[1:]
                for t in present:
                    if ann.endswith('Invoker;'):
                        ok = jar.resolve('method', t, name, m['desc'] if name != '<init>' else None)
                    else:
                        ok = jar.resolve('field', t, name, None)
                    if ok is False:
                        probs.add('@%s %s not in %s' % (ann.split('/')[-1][:-1], name, t))
    return probs


def check_class(cf, jar, game_names):
    probs = set()
    for kind, owner, name, desc in references(cf):
        if owner not in game_names:
            continue
        if kind == 'class':
            if jar.get(owner) is None:
                probs.add('class %s missing' % owner)
        elif jar.resolve(kind, owner, name, desc) is False:
            probs.add('%s %s.%s%s missing' % (kind, owner, name, '' if kind == 'field' else desc))
    # Overrides: a game method the class overrides that the game no longer declares stops being called.
    if cf.super in game_names or any(i in game_names for i in cf.interfaces):
        for m in cf.methods:
            if m['access'] & 0x000A or m['name'].startswith('<') or m['access'] & 0x1000:
                continue
            for sup in [cf.super] + cf.interfaces:
                if sup in game_names and jar.resolve('method', sup, m['name'], m['desc']) is False:
                    probs.add('override %s%s: no longer in %s' % (m['name'], m['desc'], sup))
    return probs


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        print(__doc__)
        sys.exit(2)
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dirs = args[2:] or [os.path.join(root, m, 'build', 'classes', 'java', 'main') for m in ('common', 'fabric', 'neoforge')]
    base, cand = GameJar(args[0]), GameJar(args[1])
    game_names = base.names | cand.names
    total = 0
    report = {}
    for cf in mod_classes(dirs):
        total += 1
        b = check_class(cf, base, game_names) | check_mixin(cf, base)
        c = check_class(cf, cand, game_names) | check_mixin(cf, cand)
        # An override that the baseline doesn't resolve either is an interface's own method, not a break.
        broken = sorted(c - b)
        if broken:
            report[cf.name] = broken
    print('%d mod classes checked; %d game classes in the baseline, %d in the candidate'
          % (total, len(base.names), len(cand.names)))
    if not report:
        print('Nothing the mod uses changed.')
    for name in sorted(report):
        print('\n' + name.replace('/', '.'))
        for p in report[name]:
            print('  ' + p)
    sys.exit(1 if report else 0)


if __name__ == '__main__':
    main()
