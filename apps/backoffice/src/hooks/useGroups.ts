'use client'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { groupsApi } from '@/libs/api/groups'
import type { CreateGroupRequest, UpdateGroupRequest } from '@/types/group'

export function useGroups() {
  const queryClient = useQueryClient()

  const groupsQuery = useQuery({
    queryKey: ['admin-groups'],
    queryFn: groupsApi.list
  })

  const permissionsQuery = useQuery({
    queryKey: ['admin-permissions'],
    queryFn: groupsApi.listPermissions
  })

  const createMutation = useMutation({
    mutationFn: (body: CreateGroupRequest) => groupsApi.create(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
    }
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateGroupRequest }) => groupsApi.update(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
    }
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => groupsApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
    }
  })

  const setUserGroupsMutation = useMutation({
    mutationFn: ({ userId, groupIds }: { userId: string; groupIds: string[] }) =>
      groupsApi.setUserGroups(userId, groupIds),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
      void queryClient.invalidateQueries({ queryKey: ['customers', variables.userId] })
      void queryClient.invalidateQueries({ queryKey: ['customer', variables.userId] })
    }
  })

  const addMembersMutation = useMutation({
    mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
      groupsApi.addMembers(id, userIds),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-group-members', variables.id] })
    }
  })

  const removeMembersMutation = useMutation({
    mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
      groupsApi.removeMembers(id, userIds),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-groups'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-group-members', variables.id] })
    }
  })

  return {
    groupsQuery,
    permissionsQuery,
    createMutation,
    updateMutation,
    deleteMutation,
    setUserGroupsMutation,
    addMembersMutation,
    removeMembersMutation
  }
}

export function useGroupMembers(groupId: string, page = 0, size = 20, keyword = '') {
  return useQuery({
    queryKey: ['admin-group-members', groupId, page, size, keyword],
    queryFn: () => groupsApi.members(groupId, page, size, keyword),
    enabled: !!groupId
  })
}

export function useGroupCandidates(groupId: string, keyword = '') {
  return useQuery({
    queryKey: ['admin-group-candidates', groupId, keyword],
    queryFn: () => groupsApi.candidates(groupId, keyword),
    enabled: !!groupId && keyword.trim().length >= 2
  })
}
