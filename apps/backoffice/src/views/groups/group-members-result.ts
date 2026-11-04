import type { GroupMembersChange } from '@/types/group'
import { changeWarning } from './group-form'

export interface MemberFailureItem {
  userId: string
  email?: string | null
  code: number
  message: string
}

export interface GroupMembersSummary {
  succeededUserIds: string[]
  failures: MemberFailureItem[]
  notLoggedOutUserIds: string[]
  successNotice: string | null
  failureAlert: string | null
  logoutWarning: string | null
}

export function chunkUserIds(userIds: string[], chunkSize = 100): string[][] {
  if (chunkSize <= 0) return [userIds]

  const chunks: string[][] = []

  for (let i = 0; i < userIds.length; i += chunkSize) {
    chunks.push(userIds.slice(i, i + chunkSize))
  }

  return chunks
}

export function aggregateMemberChanges(
  results: GroupMembersChange[],
  action: 'add' | 'remove',
  userEmailMap: Record<string, string | null> = {}
): GroupMembersSummary {
  const succeededUserIds: string[] = []
  const failures: MemberFailureItem[] = []
  const notLoggedOutUserIds: string[] = []

  for (const res of results) {
    if (res.succeededUserIds) {
      succeededUserIds.push(...res.succeededUserIds)
    }

    if (res.failures) {
      for (const f of res.failures) {
        failures.push({
          userId: f.userId,
          email: userEmailMap[f.userId] ?? null,
          code: f.code,
          message: f.message
        })
      }
    }

    if (res.notLoggedOutUserIds) {
      notLoggedOutUserIds.push(...res.notLoggedOutUserIds)
    }
  }

  const succeededCount = succeededUserIds.length
  const failedCount = failures.length

  let successNotice: string | null = null
  let failureAlert: string | null = null

  if (failedCount === 0 && succeededCount > 0) {
    successNotice =
      action === 'add'
        ? `Đã thêm thành công ${succeededCount} thành viên vào nhóm.`
        : `Đã xóa thành công ${succeededCount} thành viên khỏi nhóm.`
  } else if (failedCount > 0) {
    failureAlert = `${succeededCount > 0 ? `${succeededCount} thành công, ` : ''}${failedCount} thất bại.`
  }

  const logoutWarning = changeWarning({ notLoggedOutUserIds })

  return {
    succeededUserIds,
    failures,
    notLoggedOutUserIds,
    successNotice,
    failureAlert,
    logoutWarning
  }
}
