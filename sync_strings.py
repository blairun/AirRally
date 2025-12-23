import os
import re

# Configuration
ENGLISH_FILE = 'app/src/main/res/values/strings.xml'
VALUES_DIR = 'app/src/main/res'

def load_translations(file_path):
    """
    Reads a strings.xml file and returns a dict of {name: content}.
    Uses regex to preserve exact XML content within the tags.
    """
    if not os.path.exists(file_path):
        return {}
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Regex to capture name and content. 
    # Flags: Dotall to handle multi-line strings if any.
    pattern = re.compile(r'<string\s+[^>]*name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)
    
    translations = {}
    for match in pattern.finditer(content):
        name = match.group(1)
        value = match.group(2)
        translations[name] = value
    
    return translations

def sync_file(target_dir_name):
    # Skip values-night or other non-language folders if they exist (standard is values-xx)
    if not target_dir_name.startswith('values-'):
        return

    target_file = os.path.join(VALUES_DIR, target_dir_name, 'strings.xml')
    if not os.path.exists(target_file):
        return

    print(f"Syncing {target_file}...")
    
    current_translations = load_translations(target_file)
    
    new_lines = []
    
    with open(ENGLISH_FILE, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    # Pattern to parse the English file lines
    # This assumes English strings are single-line as observed in the file.
    pattern = re.compile(r'(<string\s+[^>]*name=")([^"]+)("[^>]*>)(.*?)(</string>)')

    for line in lines:
        match = pattern.search(line)
        if match:
            prefix_start = match.group(1)
            key = match.group(2)
            prefix_end = match.group(3)
            # english_value = match.group(4)
            suffix = match.group(5)
            
            # Preserve indentation/whitespace before the tag
            line_start = line[:match.start()]
            # Preserve newline/whitespace after the tag
            line_end = line[match.end():] 
            
            if key in current_translations:
                # Use existing translation
                localized_value = current_translations[key]
                new_line = f"{line_start}{prefix_start}{key}{prefix_end}{localized_value}{suffix}{line_end}"
                new_lines.append(new_line)
            else:
                # Use English value prefixed with TODO
                english_value = match.group(4)
                # If it already has TODO, don't double add (in case we are re-syncing over a file that was just synced?)
                # Actually, we take english_value from the SOURCE (English file), so it won't have TODO unless English has it.
                # So we are safe.
                new_value = f"TODO: {english_value}"
                new_line = f"{line_start}{prefix_start}{key}{prefix_end}{new_value}{suffix}{line_end}"
                new_lines.append(new_line)
        else:
            # Comment, whitespace, or other tags
            new_lines.append(line)
            
    # Write back
    with open(target_file, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

def main():
    print(f"Source: {ENGLISH_FILE}")
    if not os.path.exists(ENGLISH_FILE):
        print("Error: English file not found!")
        return

    target_dirs = [d for d in os.listdir(VALUES_DIR) if d.startswith('values-') and os.path.isdir(os.path.join(VALUES_DIR, d))]
    print(f"Found {len(target_dirs)} target directories: {target_dirs}")
    
    for d in target_dirs:
        sync_file(d)
    
    print("Synchronization complete.")

if __name__ == "__main__":
    main()
