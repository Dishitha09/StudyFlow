# Stage 10 · Study Materials and Study Assistant

## Implementation

Reviewed the typed file repositories, subject relationships and worker-backed Swing pages. Added `StudyMaterial`, `MaterialRepository`, `MaterialLibrary`, `AIProvider`, `StudyAssistant`, and the Materials views. The profile and desktop controller connect the new services to subject records and Global Search.

The source PDF, material metadata and text index are stored separately. PDFBox 3.0.8 extracts each original page on import. The library splits text into bounded passages and retrieves them locally with the original file name and page. The assistant separates local extracts and source passages from optional generated output. Its provider contract keeps transport outside the UI.

## Verification and limitations

All 101 tests passed. Four new PDF tests cover managed copies, source page references, cached restart, selected and global retrieval, invalid files, document removal and PDFs with no text. Local study modes use extractive summaries and practice prompts. Scanned documents can be stored and opened, with OCR reserved for future work.

## Next stage

Stage 11 can wire a configured external generator while keeping local study available.
