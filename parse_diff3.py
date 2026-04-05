import json

with open('diff_output.json') as f:
    data = json.load(f)

files_to_check = [
    "README.md",
    "application/USAGE.md",
    "application/src/main/java/com/novel/splitter/application/controller/DownloadController.java",
    "application/src/main/java/com/novel/splitter/application/controller/NovelController.java",
    "application/src/main/java/com/novel/splitter/application/controller/SplitController.java"
]

for f in files_to_check:
    diff = data.get(f)
    if not diff:
        continue
    
    print(f"\n[{f}]")
    for line in diff.split('\n'):
        if line.startswith('+') or line.startswith('-'):
            if not line.startswith('+++') and not line.startswith('---'):
                print(line)

