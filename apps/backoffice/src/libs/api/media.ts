import { api, ApiError } from './client'
import type { PageDto } from '@/types/api'
import type { MediaFile, MediaQuery } from '@/types/media'

export const mediaApi = {
  list: (params: MediaQuery) => {
    const query = new URLSearchParams()

    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value))
    })

    return api.get<PageDto<MediaFile>>('/api/v1/files?' + query)
  },
  get: (id: string) => api.get<MediaFile>('/api/v1/files/' + encodeURIComponent(id)),
  update: (id: string, body: Pick<MediaFile, 'displayName' | 'altText' | 'folder' | 'tags'>) =>
    api.patch<MediaFile>('/api/v1/files/' + encodeURIComponent(id), body),
  trash: (id: string) => api.delete<void>('/api/v1/files/' + encodeURIComponent(id)),
  restore: (id: string) => api.post<MediaFile>(`/api/v1/files/${encodeURIComponent(id)}/restore`),
  permanent: (id: string) => api.delete<void>(`/api/v1/files/${encodeURIComponent(id)}/permanent`),
  upload: async (file: File): Promise<MediaFile> => {
    const signed = await api.post<{
      mediaId: string
      uploadUrl: string
      contentType: string
      uploadHeaders: Record<string, string>
    }>('/api/v1/files/presign', {
      filename: file.name,
      contentType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      visibility: 'PUBLIC'
    })

    const url = new URL(signed.uploadUrl)

    if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Đường dẫn tải ảnh chưa hợp lệ.')

    const response = await fetch(signed.uploadUrl, {
      method: 'PUT',
      headers: signed.uploadHeaders,
      body: file,
      credentials: 'omit'
    })

    if (!response.ok)
      throw new ApiError(response.status, response.status, 'Không tải được file lên kho lưu trữ. Vui lòng thử lại.')

    return api.post<MediaFile>(`/api/v1/files/${encodeURIComponent(signed.mediaId)}/complete`, {})
  }
}
