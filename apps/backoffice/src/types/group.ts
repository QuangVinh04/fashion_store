import type { PermissionName } from '@/configs/permissions'

export interface PermissionItem {
  authority: PermissionName
  area: string
  label: string
  isAssignable: boolean
}

export interface Group {
  id: string
  name: string
  permissions: PermissionName[]
  memberCount: number | null
  isSystem: boolean
  isEditable: boolean
}

export interface GroupChange {
  group: Group
  notLoggedOutUserIds: string[]
}

export interface UserGroups {
  groups: Group[]
  notLoggedOutUserIds: string[]
}

export interface GroupMember {
  userId: string
  email: string | null
  fullName: string | null
  isActive: boolean | null
}

export interface GroupMembersPage {
  items: GroupMember[]
  totalItems?: number
  totalElements?: number
  totalPages?: number
  totalPage?: number
  page?: number
  pageNo?: number
  size?: number
  pageSize?: number
}

export interface GroupMemberFailure {
  userId: string
  code: number
  message: string
}

export interface GroupMembersChange {
  succeededUserIds: string[]
  failures: GroupMemberFailure[]
  notLoggedOutUserIds: string[]
}

export interface GroupMembersRequest {
  userIds: string[]
}

export interface CreateGroupRequest {
  name: string
  permissions: PermissionName[]
}

export interface UpdateGroupRequest {
  name: string
  permissions: PermissionName[]
}

export interface CreateStaffRequest {
  email: string
  firstName: string
  lastName: string
  temporaryPassword: string
  groupIds: string[]
}

export interface Staff {
  id: string
  email: string
  fullName: string
  groups: Group[]
}
