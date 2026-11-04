import type { PermissionName } from '@/configs/permissions'
import type { PermissionItem } from '@/types/group'

export interface AreaGroup {
  area: string
  items: PermissionItem[]
}

export function groupPermissionsByArea(items: PermissionItem[]): AreaGroup[] {
  const result: AreaGroup[] = []
  const areaIndexMap = new Map<string, number>()

  for (const item of items) {
    let index = areaIndexMap.get(item.area)

    if (index === undefined) {
      index = result.length
      areaIndexMap.set(item.area, index)
      result.push({ area: item.area, items: [] })
    }

    result[index].items.push(item)
  }

  return result
}

export function toggleArea(
  selected: Set<PermissionName>,
  areaItems: PermissionItem[],
  checked: boolean
): Set<PermissionName> {
  const next = new Set(selected)

  for (const item of areaItems) {
    if (checked) {
      next.add(item.authority)
    } else {
      next.delete(item.authority)
    }
  }

  return next
}

export function changeWarning(change: { notLoggedOutUserIds: string[] }): string | null {
  if (change?.notLoggedOutUserIds && change.notLoggedOutUserIds.length > 0) {
    return `${change.notLoggedOutUserIds.length} người dùng chưa bị đăng xuất, quyền mới có hiệu lực trong tối đa 5 phút`
  }

  return null
}
