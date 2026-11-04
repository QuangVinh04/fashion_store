import { api } from './client'
import type {
  CreateGroupRequest,
  Group,
  GroupChange,
  GroupMember,
  GroupMembersChange,
  GroupMembersPage,
  PermissionItem,
  UpdateGroupRequest,
  UserGroups
} from '@/types/group'

export const groupsApi = {
  listPermissions: () => api.get<PermissionItem[]>('/api/v1/admin/permissions'),
  list: () => api.get<Group[]>('/api/v1/admin/groups'),
  create: (body: CreateGroupRequest) => api.post<GroupChange>('/api/v1/admin/groups', body),
  update: (id: string, body: UpdateGroupRequest) => api.put<GroupChange>(`/api/v1/admin/groups/${id}`, body),
  remove: (id: string) => api.delete<string[]>(`/api/v1/admin/groups/${id}`),
  members: (id: string, page = 0, size = 20, keyword?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })

    if (keyword?.trim()) {
      params.set('keyword', keyword.trim())
    }

    return api.get<GroupMembersPage>(`/api/v1/admin/groups/${id}/members?${params.toString()}`)
  },
  candidates: (id: string, keyword: string) => {
    const params = new URLSearchParams()

    if (keyword?.trim()) {
      params.set('keyword', keyword.trim())
    }

    return api.get<GroupMember[]>(`/api/v1/admin/groups/${id}/candidates?${params.toString()}`)
  },
  addMembers: (id: string, userIds: string[]) =>
    api.post<GroupMembersChange>(`/api/v1/admin/groups/${id}/members`, { userIds }),
  removeMembers: (id: string, userIds: string[]) =>
    api.delete<GroupMembersChange>(`/api/v1/admin/groups/${id}/members`, {
      body: JSON.stringify({ userIds })
    }),
  setUserGroups: (userId: string, groupIds: string[]) =>
    api.put<UserGroups>(`/api/v1/admin/users/${userId}/groups`, { groupIds })
}
