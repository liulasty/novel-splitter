import json
import os
import glob

def analyze_scenes(json_path):
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    num_scenes = len(data)
    if num_scenes == 0:
        return {"error": "Empty file"}
        
    chapters = set()
    total_words = 0
    max_words = 0
    min_words = float('inf')
    
    total_density = 0
    total_quality = 0
    can_split_count = 0
    
    for scene in data:
        chapters.add(scene.get('chapterTitle', ''))
        word_count = scene.get('wordCount', 0)
        total_words += word_count
        max_words = max(max_words, word_count)
        min_words = min(min_words, word_count)
        
        can_split = scene.get('canSplit', False)
        if can_split:
            can_split_count += 1
            
        meta = scene.get('metadata', {})
        total_density += meta.get('densityScore', 0)
        total_quality += meta.get('qualityScore', 0)
        
    avg_words = total_words / num_scenes
    avg_density = total_density / num_scenes
    avg_quality = total_quality / num_scenes
    
    return {
        "File": os.path.basename(os.path.dirname(os.path.dirname(json_path))),
        "Total Chapters": len(chapters),
        "Total Scenes": num_scenes,
        "Average Word Count": round(avg_words, 2),
        "Max Word Count": max_words,
        "Min Word Count": min_words,
        "Average Density Score": round(avg_density, 4),
        "Average Quality Score": round(avg_quality, 4),
        "Scenes canSplit": can_split_count,
        "Scenes Cannot Split": num_scenes - can_split_count
    }

def main():
    base_dir = r"d:\soft\novel-splitter\data\novel-storage\scene"
    json_files = glob.glob(os.path.join(base_dir, "**", "scenes.json"), recursive=True)
    
    if not json_files:
        print("No scenes.json found.")
        return
        
    for jf in json_files:
        print(f"Analyzing: {jf}")
        stats = analyze_scenes(jf)
        for k, v in stats.items():
            print(f"  {k}: {v}")
        print("-" * 40)

if __name__ == "__main__":
    main()
