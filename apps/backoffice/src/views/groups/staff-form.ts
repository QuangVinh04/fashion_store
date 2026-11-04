import type { CreateStaffRequest } from '@/types/group'

export function validateStaffForm(form: CreateStaffRequest): Partial<Record<keyof CreateStaffRequest, string>> {
  const errors: Partial<Record<keyof CreateStaffRequest, string>> = {}

  if (!form.email?.trim()) {
    errors.email = 'Email không được để trống.'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'Email không đúng định dạng.'
  }

  if (!form.firstName?.trim()) {
    errors.firstName = 'Tên không được để trống.'
  }

  if (!form.lastName?.trim()) {
    errors.lastName = 'Họ không được để trống.'
  }

  if (!form.temporaryPassword?.trim()) {
    errors.temporaryPassword = 'Mật khẩu tạm không được để trống.'
  } else if (form.temporaryPassword.length < 8) {
    errors.temporaryPassword = 'Mật khẩu tạm cần ít nhất 8 ký tự.'
  }

  if (!form.groupIds || form.groupIds.length === 0) {
    errors.groupIds = 'Cần chọn ít nhất một nhóm phân quyền.'
  }

  return errors
}
