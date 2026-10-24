import type { AdminPage, PageDto } from '@/types/api'

export function normalizeAdminPage<T>(dto: PageDto<T>): AdminPage<T> {
  if (
    !Array.isArray(dto.items) ||
    !Number.isSafeInteger(dto.pageNo) ||
    dto.pageNo < 0 ||
    !Number.isSafeInteger(dto.pageSize) ||
    dto.pageSize <= 0 ||
    !Number.isSafeInteger(dto.totalPage) ||
    dto.totalPage < 0 ||
    dto.totalElements == null ||
    !Number.isSafeInteger(dto.totalElements) ||
    dto.totalElements < 0
  ) {
    throw new Error('Dữ liệu phân trang chưa đầy đủ hoặc không hợp lệ.')
  }

  return { ...dto, totalElements: dto.totalElements }
}
