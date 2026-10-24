export interface MediaFile {
  id: string
  originalFilename: string
  displayName?: string
  url?: string
  contentType?: string
  mediaType: 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'OTHER'
  status: 'PENDING' | 'TEMP' | 'ACTIVE' | 'TRASHED'
  altText?: string
  folder?: string
  tags?: string[]
  sizeBytes?: number
}

export interface MediaQuery {
  page: number
  size: number
  status?: string
  mediaType?: string
  keyword?: string
  folder?: string
}
