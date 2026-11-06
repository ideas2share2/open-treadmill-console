#!/usr/bin/env python3
"""Generic pre-publication check. No network, credential access or automatic redaction."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
exclude_parts = {'.git', '.gradle', '__pycache__'}
allowed_binary = {'.ttf', '.otf', '.jar'}
forbidden_suffixes = {'.apk', '.aab', '.dex', '.idsig', '.keystore', '.jks', '.p12', '.pem', '.key',
                      '.db', '.sqlite', '.sqlite3', '.log', '.hprof', '.har', '.pcap', '.pcapng',
                      '.jpg', '.jpeg', '.png', '.heic', '.mov', '.mp4', '.zip'}
patterns = {
    'private home path': re.compile(r'(?<![\w.])/(?:Users|home)/[^/\s]+'),
    'private build path': re.compile(r'/private/tmp/[^\s]+'),
    'private IPv4': re.compile(r'\b(?:10\.(?:\d{1,3}\.){2}\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
    'MAC-like identifier': re.compile(r'(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b'),
    'private key block': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'access-token pattern': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{25,}|AKIA[A-Z0-9]{16})\b'),
    'credential in URL': re.compile(r'https?://[^\s/@:]+:[^\s/@]+@'),
}
issues=[]
count=0
for p in sorted(root.rglob('*')):
    rel=p.relative_to(root)
    if not p.is_file() or any(x in exclude_parts for x in rel.parts) or 'build' in rel.parts:
        continue
    count+=1
    if p.is_symlink():
        issues.append((rel, 'symlink requires review'));continue
    if p.suffix.lower() in forbidden_suffixes or p.name in ('.env', 'local.properties', '.DS_Store'):
        issues.append((rel, 'private/generated artifact'));continue
    if p.suffix.lower() in allowed_binary:
        continue
    try:text=p.read_text(encoding='utf-8')
    except (UnicodeError,OSError):
        issues.append((rel,'unrecognized binary'));continue
    # This scanner necessarily contains the patterns it searches for, not actual credentials.
    if rel.as_posix()=='scripts/privacy_scan.py':continue
    for name,pattern in patterns.items():
        if pattern.search(text):issues.append((rel,name))
    for email in re.findall(r'[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}',text):
        if not email.endswith('@users.noreply.github.com'):
            issues.append((rel,'email address requires human review'))
if issues:
    for path,why in issues:print(f'REVIEW: {path}: {why}')
    sys.exit(1)
print(f'PASS: {count} files; no common private paths, addresses, token patterns or forbidden artifacts.')
print('Human review still required for names, indirect clues, binary metadata, git authors and account identity.')
