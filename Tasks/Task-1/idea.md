This pull request enhances the handling of MasterFormat-style partial numbering (e.g., .1, .2) in PDF document conversion to Markdown, ensuring that such numbering is merged with its associated text rather than split into separate lines or table columns. It also introduces comprehensive tests to verify correct behavior and bumps the package version.

Testing and validation:

Added test_pdf_masterformat.py with extensive tests to verify correct regex matching, merging logic, and content preservation for MasterFormat-style partial numbering in converted documents.
Version update:

Bumped the package version from 0.1.4 to 0.1.5 in **about**.py to reflect the new functionality and fixes.

---

## Prompt Ideas (to be rewritten in your own words)

### Option A — MasterFormat Partial Numbering Fix (Focused, Bug-Fix Style)

The PDF converter in markitdown uses pdfminer to extract text from PDF files. When converting documents that follow MasterFormat-style conventions (common in construction/engineering specs), partial numbering like `.1`, `.2`, `.10` gets extracted on its own line, separated from the text it belongs to. For example, a PDF that visually reads:

```
.1  The intent of this Request for Proposal is to...
.2  Available information relative to the existing...
```

gets extracted as:

```
.1
The intent of this Request for Proposal is to...
.2
Available information relative to the existing...
```

This breaks the semantic meaning of the content. Add post-processing to the PDF converter that detects when a line consists solely of a MasterFormat-style partial number (matching the pattern `^\.\d+$`) and merges it with the following non-empty text line. The fix should be applied after text extraction, handle edge cases like consecutive partial numbers or a partial number at the end of the document with no following text, and include tests that verify the merging logic works correctly. Make sure existing PDF conversion behavior is not broken.

---

### Option B — PDF Table Extraction as Markdown (Feature Extension)

The PDF converter currently extracts all content as flat plain text using pdfminer, which means any tables present in PDF documents are completely lost — columns get smashed together or scattered across lines with no structure. Enhance the PDF converter to detect and extract tables from PDF pages and render them as properly formatted Markdown tables with pipe-separated columns and header separator rows. You may use pdfplumber as an additional dependency for word-position analysis to identify tabular layouts. The solution should handle both bordered and borderless tables, fall back gracefully to the existing pdfminer text extraction for pages that don't contain tables, and include tests. Ensure the existing conversion behavior for non-tabular PDFs remains unchanged.
