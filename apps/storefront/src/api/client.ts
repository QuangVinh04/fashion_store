export type ApiEnvelope<T> = { code: number; message?: string; data?: T };
export type Page<T> = { pageNo: number; pageSize: number; totalPage: number; items: T[] };

export class ApiError extends Error {
  constructor(public status: number, public code: number | undefined, message: string) {
    super(message);
  }
}

export function customerError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (error.status === 403) return 'Không thể thực hiện thao tác này. Vui lòng kiểm tra tài khoản hoặc tải lại trang.';
    if (error.status === 404) return 'Chưa tìm thấy thông tin bạn cần. Vui lòng thử lại hoặc liên hệ shop.';
    if (error.status === 409) return 'Thông tin đã thay đổi. Vui lòng cập nhật lại trước khi tiếp tục.';
    if (error.status === 400 || error.status === 422) return 'Không thể thực hiện yêu cầu. Vui lòng kiểm tra thông tin đã nhập.';
    if (error.status === 429) return 'Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.';
  }
  return 'Không thể xử lý yêu cầu lúc này. Vui lòng thử lại hoặc liên hệ shop.';
}

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find(part => part.startsWith(`${name}=`))?.slice(name.length + 1);
}

async function xsrfToken(): Promise<string> {
  let token = cookie('FS_STOREFRONT_XSRF');
  if (!token) {
    // The BFF issues the readable CSRF cookie on a safe request. OAuth login can clear it.
    const response = await fetch('/bff/session', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new ApiError(response.status, undefined, 'Không làm mới được phiên bảo mật. Vui lòng tải lại trang.');
    token = cookie('FS_STOREFRONT_XSRF');
  }
  if (!token) throw new ApiError(403, undefined, 'Không lấy được mã bảo mật phiên. Vui lòng tải lại trang.');
  return decodeURIComponent(token);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase();
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method);
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (unsafe) {
    headers.set('X-XSRF-TOKEN', await xsrfToken());
  }
  let response: Response;
  try {
    response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' });
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(0, undefined, 'Không kết nối được máy chủ. Vui lòng thử lại.');
  }
  const contentType = response.headers.get('content-type') || '';
  const payload: ApiEnvelope<T> | undefined = contentType.includes('application/json')
    ? await response.json().catch(() => undefined) : undefined;
  if (!response.ok || (payload && payload.code !== 1000)) {
    throw new ApiError(response.status, payload?.code, payload?.message || `Yêu cầu thất bại (${response.status}).`);
  }
  if (!payload) throw new ApiError(response.status, undefined, 'Phản hồi máy chủ không hợp lệ.');
  return payload.data as T;
}

export const json = (method: string, body?: unknown, headers?: HeadersInit): RequestInit => ({
  method, headers, body: body === undefined ? undefined : JSON.stringify(body),
});

export function query(values: Record<string, string | number | undefined | null>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  });
  return params.toString();
}

export const money = (value: number | null | undefined) => new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', maximumFractionDigits: 0,
}).format(value ?? 0);

export const media = (id?: string | null) => id ? `/api/v1/files/${encodeURIComponent(id)}/content` : '';
