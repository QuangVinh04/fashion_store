import { ApiError } from './client'

const messages: Record<number, string> = {
  1002: 'Tên hoặc đường dẫn danh mục đã tồn tại.',
  1005: 'Danh mục còn sản phẩm hoặc danh mục con. Hãy chuyển chúng trước khi xóa.',
  1006: 'Danh mục cha không được là chính nó hoặc danh mục con của nó.',
  4002: 'Không thể chuyển trạng thái đơn theo bước này. Hãy tải lại đơn để kiểm tra.',
  4017: 'Địa chỉ giao hàng thiếu tỉnh hoặc phường hợp lệ của GHN.',
  4018: 'Cần tạo vận đơn và mã theo dõi trước khi chuyển sang Đang giao.',
  4055: 'Tồn thực tế không được thấp hơn số lượng đang giữ cho đơn hàng.',
  4056: 'Mã thao tác đã được dùng cho một yêu cầu khác. Hãy kiểm tra nhật ký kho.',
  1007: 'Bộ lọc sản phẩm chưa hợp lệ. Vui lòng kiểm tra lại.',
  1016: 'Tên thuộc tính hoặc giá trị tùy chọn đã tồn tại.',
  1025: 'Màu và kích cỡ được quản lý trong phần mẫu mã.',
  1026: 'Thuộc tính đang được sản phẩm sử dụng. Không thể xóa hoặc đổi kiểu dữ liệu.',
  1027: 'Tùy chọn đang được sản phẩm sử dụng. Không thể xóa hoặc đổi giá trị.',
  1041: 'Giá trị tối đa phải lớn hơn hoặc bằng giá trị tối thiểu.',
  1043: 'Mã kích cỡ đã có trong bảng này. Vui lòng chọn mã khác.',
  2005: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  2006: 'Bạn không có quyền thực hiện thao tác này.',
  2007: 'Bạn không có quyền thực hiện thao tác này.',
  9001: 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra các trường được đánh dấu.',
  9002: 'Dữ liệu gửi lên chưa đúng định dạng.',
  9003: 'Bạn không có quyền thực hiện thao tác này.'
}

function vietnameseValidation(message: string): string {
  if (/[^\u0000-\u007f]/.test(message)) return message
  if (/must not be (blank|null|empty)|required/i.test(message)) return 'Vui lòng nhập giá trị.'
  if (/must be greater than or equal to 0|must be positive or zero/i.test(message))
    return 'Giá trị phải lớn hơn hoặc bằng 0.'
  if (/already exists|duplicate/i.test(message)) return 'Giá trị này đã tồn tại.'
  if (/valid.*email|email.*valid/i.test(message)) return 'Địa chỉ email chưa hợp lệ.'

  return 'Giá trị chưa hợp lệ. Vui lòng kiểm tra lại.'
}

export function mapFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || !error.fieldErrors) return {}

  return Object.fromEntries(
    Object.entries(error.fieldErrors).map(([path, message]) => [path, vietnameseValidation(message)])
  )
}

export function toVietnameseError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.'
    if (error.status === 401) return messages[2005]
    if (error.status === 403) return messages[2007]
    if (messages[error.code]) return messages[error.code]
    if (/[^\u0000-\u007f]/.test(error.message)) return error.message
    if (error.status === 404) return 'Không tìm thấy dữ liệu yêu cầu.'
    if (error.status === 409) return 'Dữ liệu đã thay đổi hoặc đang được sử dụng. Vui lòng kiểm tra và thử lại.'
    if (error.status >= 500) return 'Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau.'
    if (error.fieldErrors) return messages[9001]
  }

  if (error instanceof TypeError) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.'

  return 'Không thể hoàn tất thao tác. Vui lòng thử lại.'
}
