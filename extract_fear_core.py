#!/usr/bin/env python3
"""
FEAR LAUNCHER Core Extraction Workflow
======================================
Scans a PojavLauncher-based repo, extracts only essential 
rendering, JVM, input, and settings code into a clean branch.
"""

import os
import re
import sys
import shutil
import subprocess
from pathlib import Path
from typing import List, Set, Tuple

# ==================== CONFIGURATION ====================
OUTPUT_BRANCH = "fear-core"
EXTRACTED_DIR = "fear_core_extracted"

# ✅ KEEP: Core functionality patterns (case-insensitive regex)
KEEP_PATTERNS = [
    # Rendering & Graphics
    r"renderer", r"render", r"zink", r"ltw", r"gl4es", r"vulkan", 
    r"opengl", r"gles", r"shader", r"texture", r"framebuffer",
    r"egl", r"surface", r"swapchain", r"pipeline",
    
    # JVM & Launch
    r"jvm", r"java.*home", r"launch", r"classpath", r"nativelib",
    r"args", r"forge", r"fabric", r"optifine", r"sodium",
    
    # Input & Controls
    r"input", r"touch", r"controller", r"keymap", r"mouse", 
    r"joystick", r"gesture", r"virtual.*key",
    
    # Version & Download Management
    r"version.*manag", r"download", r"asset.*down", r"library.*down",
    r"auth", r"microsoft", r"mojang", r"profile",
    
    # Settings & Config
    r"setting", r"config", r"preference", r"option",
    
    # JNI / Native Bridge
    r"jni", r"native", r"bridge", r"c2s", r"s2c",
]

# ❌ EXCLUDE: UI, Legacy Activities, Docs, Build artifacts
EXCLUDE_DIR_PATTERNS = [
    "activities", "activity", "ui/", "layout", "drawable", 
    "mipmap", "values", "menu", "anim", "xml/res",    "docs", ".github", ".git", "build", ".gradle", 
    "test", "androidTest", "debug"
]

EXCLUDE_FILE_PATTERNS = [
    r"mainactivity\.(java|kt)$",
    r"pojavapplication\.(java|kt)$",
    r".*adapter\.(java|kt)$",      # UI adapters
    r".*fragment\.(java|kt)$",     # UI fragments  
    r".*dialog\.(java|kt)$",       # UI dialogs
    r".*view\.(java|kt)$",         # Custom views
    r".*widget\.(java|kt)$",       # UI widgets
    r"androidmanifest\.xml$",      # Will regenerate later
    r".*\.png$", r".*\.jpg$", r".*\.svg$",  # Images
    r".*\.md$", r".*\.txt$",       # Docs
]

# File extensions to consider as source code
SOURCE_EXTENSIONS = {".java", ".kt", ".c", ".cpp", ".h", ".hpp", ".rs", ".glsl", ".vert", ".frag"}


# ==================== CORE LOGIC ====================

def is_source_file(filepath: str) -> bool:
    """Check if file is a relevant source code file."""
    return Path(filepath).suffix.lower() in SOURCE_EXTENSIONS


def matches_any_pattern(text: str, patterns: List[str]) -> bool:
    """Check if text matches any regex pattern (case-insensitive)."""
    text_lower = text.lower().replace("\\", "/")
    for pattern in patterns:
        if re.search(pattern, text_lower):
            return True
    return False


def should_extract(filepath: str, repo_root: str) -> bool:
    """Determine if a file should be extracted to core branch."""
    rel_path = os.path.relpath(filepath, repo_root).replace("\\", "/")
    
    # Must be a source file
    if not is_source_file(filepath):
        return False
    
    # Explicit excludes first
    if matches_any_pattern(rel_path, EXCLUDE_DIR_PATTERNS):
        return False
    if matches_any_pattern(os.path.basename(filepath), EXCLUDE_FILE_PATTERNS):
        return False    
    # Must match at least one KEEP pattern
    if matches_any_pattern(rel_path, KEEP_PATTERNS):
        return True
    
    return False


def scan_repository(repo_root: str) -> List[str]:
    """Scan repo and return list of files to extract."""
    extracted_files = []
    total_scanned = 0
    
    print("🔍 Scanning repository...")
    for root, dirs, files in os.walk(repo_root):
        # Prune excluded directories early for performance
        dirs[:] = [d for d in dirs if not matches_any_pattern(
            os.path.join(root, d).replace("\\", "/"), EXCLUDE_DIR_PATTERNS
        )]
        
        for filename in files:
            filepath = os.path.join(root, filename)
            total_scanned += 1
            
            if should_extract(filepath, repo_root):
                extracted_files.append(filepath)
    
    print(f"   Scanned {total_scanned} files, found {len(extracted_files)} core files")
    return extracted_files


def copy_files(files: List[str], repo_root: str, output_dir: str) -> None:
    """Copy extracted files preserving directory structure."""
    print(f"📦 Copying files to '{output_dir}'...")
    
    for filepath in files:
        rel_path = os.path.relpath(filepath, repo_root)
        dest_path = os.path.join(output_dir, rel_path)
        
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        shutil.copy2(filepath, dest_path)


def create_branch(branch_name: str) -> None:
    """Create and checkout new git branch."""
    print(f"🔀 Creating branch '{branch_name}'...")
    
    # Check if branch already exists
    result = subprocess.run(
        ["git", "rev-parse", "--verify", branch_name],        capture_output=True, text=True
    )
    
    if result.returncode == 0:
        print(f"   ⚠️  Branch '{branch_name}' already exists!")
        response = input("   Delete and recreate? (y/N): ").strip().lower()
        if response != 'y':
            print("   Aborting. Use existing branch or choose different name.")
            sys.exit(1)
        subprocess.run(["git", "branch", "-D", branch_name], check=True)
    
    subprocess.run(["git", "checkout", "-b", branch_name], check=True)


def print_summary(files: List[str], repo_root: str) -> None:
    """Print extraction summary with categorized files."""
    categories = {
        "Rendering": [], "JVM/Launch": [], "Input": [],
        "Version/Auth": [], "Settings": [], "Native/JNI": [], "Other": []
    }
    
    for f in files:
        rel = os.path.relpath(f, repo_root).lower()
        if any(re.search(p, rel) for p in KEEP_PATTERNS[:8]):
            categories["Rendering"].append(rel)
        elif any(re.search(p, rel) for p in KEEP_PATTERNS[8:14]):
            categories["JVM/Launch"].append(rel)
        elif any(re.search(p, rel) for p in KEEP_PATTERNS[14:20]):
            categories["Input"].append(rel)
        elif any(re.search(p, rel) for p in KEEP_PATTERNS[20:26]):
            categories["Version/Auth"].append(rel)
        elif any(re.search(p, rel) for p in KEEP_PATTERNS[26:28]):
            categories["Settings"].append(rel)
        elif any(re.search(p, rel) for p in KEEP_PATTERNS[28:]):
            categories["Native/JNI"].append(rel)
        else:
            categories["Other"].append(rel)
    
    print("\n" + "=" * 60)
    print("✅ EXTRACTION COMPLETE")
    print("=" * 60)
    print(f"Total core files extracted: {len(files)}\n")
    
    for category, cat_files in categories.items():
        if cat_files:
            print(f"📁 {category} ({len(cat_files)} files):")
            for cf in cat_files[:5]:
                print(f"   • {cf}")
            if len(cat_files) > 5:
                print(f"   ... and {len(cat_files) - 5} more")            print()
    
    print("=" * 60)
    print("⚡ NEXT STEPS:")
    print(f"  1. Review: cd {EXTRACTED_DIR} && find . -type f | less")
    print(f"  2. Remove leftover Pojav references:")
    print(f"     grep -ri 'pojavaluncher' {EXTRACTED_DIR}/ --include='*.java' --include='*.kt'")
    print(f"  3. Commit clean core:")
    print(f"     git add {EXTRACTED_DIR}/")
    print(f"     git commit -m 'feat: extract clean core for FEAR LAUNCHER'")
    print(f"  4. Start building new UI on this branch!")
    print("=" * 60)


# ==================== MAIN ENTRY POINT ====================

def main():
    repo_root = os.getcwd()
    output_dir = os.path.join(repo_root, EXTRACTED_DIR)
    
    print("🚀 FEAR LAUNCHER Core Extraction Workflow")
    print(f"   Repo: {repo_root}")
    print(f"   Target Branch: {OUTPUT_BRANCH}")
    print(f"   Output Dir: {output_dir}\n")
    
    # Safety check
    if not os.path.exists(os.path.join(repo_root, ".git")):
        print("❌ ERROR: Not a git repository! Run from repo root.")
        sys.exit(1)
    
    # Step 1: Create branch
    create_branch(OUTPUT_BRANCH)
    
    # Step 2: Clean previous extraction
    if os.path.exists(output_dir):
        print(f"🧹 Cleaning previous extraction...")
        shutil.rmtree(output_dir)
    
    # Step 3: Scan & Extract
    files = scan_repository(repo_root)
    
    if not files:
        print("❌ No core files found! Check KEEP_PATTERNS configuration.")
        sys.exit(1)
    
    copy_files(files, repo_root, output_dir)
    
    # Step 4: Summary
    print_summary(files, repo_root)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⛔ Aborted by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
