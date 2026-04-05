import json

with open('diff_output.json') as f:
    data = json.load(f)

for f, diff in data.items():
    if not diff:
        continue
    
    print(f"\n[{f}]")
    for line in diff.split('\n'):
        if line.startswith('+') or line.startswith('-'):
            if not line.startswith('+++') and not line.startswith('---'):
                print(line)

