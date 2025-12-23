
# Localization Workflow

## Adding New Strings
1. Add new strings to `app/src/main/res/values/strings.xml` (English).
2. Run the synchronization script:
   ```bash
   python sync_strings.py
   ```
   This will:
   - Add new strings to all localized files (prefixed with `TODO:`).
   - Reorder strings to match English.
   - Remove obsolete strings.

## Translating
1. Open each localized file (e.g., `values-es/strings.xml`).
2. Search for `TODO:`.
3. Replace the placeholder with the correct translation.
4. Verify by building the app or visually inspecting.