import json
with open('diff_output.json') as f:
    data = json.load(f)

for k, v in data.items():
    if v:
        print(f"--- FILE: {k} ---")
        lines = v.split('\n')
        for line in lines:
            if line.startswith('+') or line.startswith('-') or line.startswith('@@'):
                if not line.startswith('+++') and not line.startswith('---'):
                    print(line[:100]) # Truncate for brevity
