# Android catalogue storage

`:core:storage` implements the JVM `LibraryPersistence` contract using Room and Android Keystore. Keeping Android storage separate preserves the existing fast JVM parser/loader tests in `:core:data`.

The current schema stores one active source, root catalogue and source-scoped identity mappings. Provider IDs are namespaced by kind. M3U locators become private keyed digests with occurrence markers so duplicates remain distinct; they never become public/raw URL IDs. User-state matching for ambiguous duplicates must be handled conservatively when favourites/resume are added.

Source configuration and a random catalogue data key are wrapped with Keystore AES-GCM. Per-entry URLs use that data key, random GCM IVs and source/generation/item associated data. Labels/categories remain app-private plain database columns for future indexing. No query callback/logging or destructive migration fallback is configured. Versioned schemas live in `schemas/`.

See [behavior and verification](../../docs/development/saved-library.md). This module does not implement multiple-source management, Paging/search/EPG, episode caching or playback resume.
