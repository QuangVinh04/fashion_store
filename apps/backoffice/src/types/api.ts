export interface PageDto<T> {
  pageNo: number
  pageSize: number
  totalPage: number
  totalElements?: number | null
  items: T[]
}

export interface AdminPage<T> extends PageDto<T> {
  totalElements: number
}
