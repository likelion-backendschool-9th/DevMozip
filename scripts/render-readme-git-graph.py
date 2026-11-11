#!/usr/bin/env python3
"""Snapshot every commit reachable from origin's remote branches into SVG pages.

Run git fetch origin first. Uses only Python's standard library and git.
"""
from html import escape
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/assets/readme/git-history'
PAGE_LINES = 75


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


raw = git('log', '--graph', '--color=never', '--date-order',
          '--pretty=format:%h %s', '--remotes=origin')
lines = raw.splitlines()
commits = git('log', '--date-order', '--format=%H', '--remotes=origin').splitlines()
by_short = {git('rev-parse', '--short', sha): sha for sha in commits}
refs = git('for-each-ref', '--format=%(refname:short) %(objectname)',
           'refs/remotes/origin').splitlines()
pages = [lines[i:i + PAGE_LINES] for i in range(0, len(lines), PAGE_LINES)]
OUT.mkdir(parents=True, exist_ok=True)
manifest = {'commitCount': len(commits), 'pageCount': len(pages),
            'main': git('rev-parse', 'origin/main'),
            'develop': git('rev-parse', 'origin/develop'),
            'refs': refs, 'commits': commits, 'pages': []}
for index, rows in enumerate(pages, 1):
    height = 136 + len(rows) * 23 + 34
    count = 0
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="{height}" viewBox="0 0 1440 {height}" role="img" aria-labelledby="title desc">',
           f'<title id="title">DevMozip actual Git history — {index}/{len(pages)}</title>',
           '<desc id="desc">Actual origin remote history, newest first. Lines continue between pages.</desc>',
           '<rect width="1440" height="100%" rx="16" fill="#faf9fd"/>',
           '<rect width="7" height="100%" rx="3" fill="#00afc1"/>',
           f'<text x="34" y="42" font-family="Pretendard,sans-serif" font-size="22" font-weight="700" fill="#292235">DevMozip · Git history {index:02d} / {len(pages):02d}</text>',
           f'<text x="34" y="73" font-family="Pretendard,sans-serif" font-size="14" fill="#71657f">All origin branches · {len(commits)} commits · newest → oldest</text>',
           f'<text x="34" y="98" font-family="Pretendard,sans-serif" font-size="13" fill="#71657f">main {manifest["main"][:7]} · develop {manifest["develop"][:7]} · graph lines {(index-1)*PAGE_LINES+1}–{(index-1)*PAGE_LINES+len(rows)}</text>']
    for i, line in enumerate(rows):
        y = 136 + i * 23
        match = re.search(r'\b([0-9a-f]{7,})\b', line)
        prefix = line[:match.start()] if match else line
        for column, char in enumerate(prefix):
            x = 38.2 + column * 8.43
            if char == '*':
                svg.append(f'<circle cx="{x:.1f}" cy="{y-4}" r="4" fill="#7955df"/>')
            elif char in '|/\\_':
                if char == '|':
                    x1, x2, y1, y2 = x, x, y-15, y+8
                elif char == '/':
                    x1, x2, y1, y2 = x+4.2, x-4.2, y-15, y+8
                elif char == '\\':
                    x1, x2, y1, y2 = x-4.2, x+4.2, y-15, y+8
                else:
                    x1, x2, y1, y2 = x-4.2, x+4.2, y+8, y+8
                svg.append(f'<line x1="{x1:.1f}" y1="{y1}" x2="{x2:.1f}" y2="{y2}" stroke="#a58adf" stroke-width="1.5" stroke-linecap="round"/>')
        if match:
            sha = match.group(1)
            count += 1
            full = by_short.get(sha) or git('rev-parse', sha)
            subject = line[match.end():].strip()
            # Keep complete commit subjects in each SVG title and the source graph.
            label = subject if len(subject) <= 110 else subject[:107] + '…'
            x = 34 + len(prefix) * 8.43
            svg.append(f'<a href="https://github.com/likelion-backendschool-9th/DevMozip/commit/{full}"><title>{escape(subject)}</title><text x="{x:.1f}" y="{y}" font-family="Pretendard,sans-serif" font-size="14" fill="#00899b">{sha}</text></a>')
            svg.append(f'<text x="{x+82:.1f}" y="{y}" font-family="Pretendard,sans-serif" font-size="14" fill="#342d40">{escape(label)}</text>')
    svg.append('</svg>')
    name = f'part-{index:02d}'
    (OUT / f'{name}.svg').write_text('\n'.join(svg) + '\n')
    manifest['pages'].append({'name': name, 'commits': count, 'lines': len(rows)})
(OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
print(f'{len(commits)} actual commits · {len(pages)} pages → {OUT.relative_to(ROOT)}')
