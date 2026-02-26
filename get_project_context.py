import os
import pyperclip

# --- Configuration ---
# Folders to ignore
ignore_folders = {'.venv', '__pycache__', '.git', '.idea', 'node_modules', 'instance', 'gradle', '.gradle', '.idea', 'build', 'run', 'generated'}

# Files to ignore
ignore_files = {'export_project_context.py', '.DS_Store', '.gitignore', 'README.md', 'next_step.md', 'LICENSE'}

# Map file extensions to Markdown language identifiers
language_map = {
    '.py': 'python', '.js': 'javascript', '.ts': 'typescript',
    '.html': 'html', '.css': 'css', '.scss': 'scss',
    '.json': 'json', '.xml': 'xml', '.yaml': 'yaml', '.yml': 'yaml',
    '.md': 'markdown', '.sh': 'shell', '.java': 'java', '.c': 'c',
    '.cpp': 'cpp', '.cs': 'csharp', '.go': 'go', '.rs': 'rust',
    '.php': 'php', '.rb': 'ruby', '.kt': 'kotlin', '.swift': 'swift',
    '.sql': 'sql', '.dockerfile': 'dockerfile', 'Dockerfile': 'dockerfile'
}

def format_size(num_bytes):
    """Converts a number of bytes to a human-readable string (e.g., 1.23 KB)."""
    if num_bytes < 1024:
        return f"{num_bytes} B"
    for unit in ['KB', 'MB', 'GB']:
        num_bytes /= 1024.0
        if num_bytes < 1024.0:
            return f"{num_bytes:.2f} {unit}"
    return f"{num_bytes:.2f} TB"

def copy_project_context_to_clipboard(startpath):
    """
    Walks through a project, formats the content into a single Markdown string,
    and copies it to the clipboard.
    """
    output_blocks = []
    
    # Add a main header for the project context
    project_name = os.path.basename(os.path.abspath(startpath))
    output_blocks.append(f"# Context for Project: {project_name}\n")

    for root, dirs, files in os.walk(startpath, topdown=True):
        # Filter out ignored directories
        dirs[:] = [d for d in dirs if d not in ignore_folders]

        for f in sorted(files):
            if f in ignore_files:
                continue
            
            file_path = os.path.join(root, f)
            relative_path = os.path.relpath(file_path, startpath)

            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
                    content = file.read()
                
                # Get the file extension and corresponding language for the code block
                _, extension = os.path.splitext(f)
                language = language_map.get(extension, '')

                # Format as a Markdown block
                output_blocks.append(f"## `{relative_path}`\n\n```{language}\n{content}\n```\n")

            except Exception as e:
                output_blocks.append(f"## `{relative_path}`\n\n```\nCould not read file: {e}\n```\n")

    # Join all the blocks into a single string with separation
    # print(output_blocks)
    full_context = "\n---\n\n".join(output_blocks)

    pyperclip.copy(str(full_context))

    # Calculate and display the size
    size_in_bytes = len(full_context.encode('utf-8'))
    human_readable_size = format_size(size_in_bytes)
    
    print(f"Project context formatted as Markdown and copied to clipboard ({human_readable_size}).")

if __name__ == "__main__":
    copy_project_context_to_clipboard('.')