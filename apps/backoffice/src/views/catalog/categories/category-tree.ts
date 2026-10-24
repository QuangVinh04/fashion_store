import type { Category } from '@/types/catalog'

export function flattenCategoryTree(
  categories: Category[],
  level = 0,
  parentName?: string
): { item: Category; level: number; parentName?: string }[] {
  return categories.flatMap(item => [
    { item, level, parentName },
    ...flattenCategoryTree(item.children || [], level + 1, item.name)
  ])
}

export function getValidParentOptions(categories: Category[], id?: string) {
  const all = flattenCategoryTree(categories)
  const current = all.find(row => row.item.id === id)
  const excluded = new Set(current ? flattenCategoryTree([current.item]).map(row => row.item.id) : [])

  return all.filter(row => !excluded.has(row.item.id))
}
