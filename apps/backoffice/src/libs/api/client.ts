export interface ApiResponse<T> {
  code: number
  message?: string
  data: T
}

export interface PageResponse<T> {
  pageNo: number
  pageSize: number
  totalPage: number
  items: T
}

export class ApiError extends Error {
  status: number
  code: number
  fieldErrors?: Record<string, string>
  correlationId?: string

  constructor(
    status: number,
    code: number,
    message: string,
    fieldErrors?: Record<string, string>,
    correlationId?: string
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.correlationId = correlationId
  }
}

function getXsrfToken(): string | null {
  if (typeof document === 'undefined') return null

  const cookie = document.cookie.split('; ').find(part => part.startsWith('FS_BACKOFFICE_XSRF='))

  return cookie ? decodeURIComponent(cookie.split('=')[1]) : null
}

let redirectingToLogin = false

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || 'GET').toUpperCase()
  const isMutation = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)

  const headers = new Headers(options.headers || {})

  headers.set('Accept', 'application/json')

  if (isMutation) {
    const xsrf = getXsrfToken()

    if (xsrf) {
      headers.set('X-XSRF-TOKEN', xsrf)
    }

    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
  }

  let res: Response

  try {
    res = await fetch(path, { ...options, headers })
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError') throw error

    throw new ApiError(0, 0, 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.')
  }

  if (res.status === 401) {
    if (typeof window !== 'undefined' && !redirectingToLogin) {
      redirectingToLogin = true
      window.location.assign('/oauth2/authorization/keycloak')
    }

    throw new ApiError(401, 401, 'Phiên đăng nhập đã hết hạn')
  }

  if (!res.ok) {
    let errorData: any = {}

    try {
      errorData = await res.json()
    } catch {
      errorData = { message: res.statusText }
    }

    throw new ApiError(
      res.status,
      errorData.code || res.status,
      errorData.message || 'Lỗi xử lý yêu cầu',
      errorData.fieldErrors,
      errorData.correlationId || res.headers.get('X-Correlation-ID') || undefined
    )
  }

  if (res.status === 204) {
    return undefined as unknown as T
  }

  const body = await res.text()

  if (!body.trim()) return undefined as T

  let json: unknown

  try {
    json = JSON.parse(body)
  } catch {
    throw new ApiError(502, 502, 'Phản hồi máy chủ chưa đúng định dạng.')
  }

  if (json && typeof json === 'object' && 'data' in json) {
    return json.data as T
  }

  return json as T
}

export const api = {
  get: <T>(url: string, init?: RequestInit) => request<T>(url, { ...init, method: 'GET' }),
  post: <T>(url: string, body?: any, init?: RequestInit) =>
    request<T>(url, {
      ...init,
      method: 'POST',
      body: body instanceof FormData ? body : JSON.stringify(body)
    }),
  put: <T>(url: string, body?: any, init?: RequestInit) =>
    request<T>(url, {
      ...init,
      method: 'PUT',
      body: body instanceof FormData ? body : JSON.stringify(body)
    }),
  patch: <T>(url: string, body?: any, init?: RequestInit) =>
    request<T>(url, {
      ...init,
      method: 'PATCH',
      body: body instanceof FormData ? body : JSON.stringify(body)
    }),
  delete: <T>(url: string, init?: RequestInit) => request<T>(url, { ...init, method: 'DELETE' })
}
