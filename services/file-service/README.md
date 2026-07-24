# File Service

Owns the media library for the platform. It stores uploaded file bytes in a
local storage adapter and stores searchable metadata in PostgreSQL.

## Capabilities

- Upload files into a central media library
- List, search, and filter media by type, status, folder, and keyword
- Update display name, alt text, folder, tags, and visibility
- Serve file content through stable media URLs
- Soft delete to trash, restore, and permanently delete files

## Package layout

```text
com.fashionstore.file
|-- config
|-- controller
|-- dto
|-- exception
|-- mapper
|-- model
|-- repository
|-- service
|-- storage
`-- FileServiceApplication
```
