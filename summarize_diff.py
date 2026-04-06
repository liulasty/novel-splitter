import re
import sys

def parse_diff(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        diff_text = f.read()

    files = {}
    current_file = None
    
    # Split the diff into files
    for line in diff_text.splitlines():
        if line.startswith('diff --git'):
            parts = line.split(' ')
            if len(parts) >= 3:
                # remove 'a/' and 'b/'
                a_file = parts[-2][2:] if parts[-2].startswith('a/') else parts[-2]
                b_file = parts[-1][2:] if parts[-1].startswith('b/') else parts[-1]
                current_file = b_file
                files[current_file] = []
        elif current_file is not None:
            files[current_file].append(line)

    return files

# We'll just ask an LLM or use basic heuristics?
# Wait, I am the LLM! I can read the diff file chunk by chunk, or just have a script extract the changes.
# Actually, I can use a script to get a summary of what changed in each file (added, removed, modified methods/lines).
