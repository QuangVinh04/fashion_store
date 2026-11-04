import { api } from './client'
import type { CreateStaffRequest, Staff } from '@/types/group'

export const staffApi = {
  create: (body: CreateStaffRequest) => api.post<Staff>('/api/v1/admin/staff', body)
}
