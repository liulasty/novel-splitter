import re
import sys

with open('/workspace/diff.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

current_file = ""
changes = []
for line in lines:
    if line.startswith('diff --git'):
        if current_file:
            print(f"File: {current_file}")
            print("".join(changes[:30]))  # limit output
            if len(changes) > 30:
                print(f"... and {len(changes) - 30} more lines")
            print("-" * 40)
        parts = line.strip().split()
        current_file = parts[-1][2:] if parts[-1].startswith('b/') else parts[-1]
        changes = []
    elif line.startswith('+') and not line.startswith('+++'):
        changes.append(line)
    elif line.startswith('-') and not line.startswith('---'):
        changes.append(line)
    elif line.startswith('@@'):
        changes.append(line.strip() + '\n')

if current_file:
    print(f"File: {current_file}")
    print("".join(changes[:30]))
    if len(changes) > 30:
        print(f"... and {len(changes) - 30} more lines")
