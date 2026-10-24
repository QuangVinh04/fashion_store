export interface AdminUser {
  id: string
  email: string
  fullName: string
  phone?: string | null
  avatar?: string | null
  isActive: boolean
  isEmailVerified: boolean
  createdAt: string
}

export interface UserAddress {
  id: string
  recipientName: string
  phone: string
  province: string
  district: string
  ward: string
  detailAddress: string
  provinceId?: number
  wardId?: number
  districtId?: number
  wardCode?: string
  isDefault: boolean
  fullAddress: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminUserDetail extends AdminUser {
  updatedAt?: string | null
  addresses: UserAddress[]
}

export interface AdminUpdateUserRequest {
  fullName: string
  phone?: string
}

export interface UpdateUserStatusRequest {
  active: boolean
}
