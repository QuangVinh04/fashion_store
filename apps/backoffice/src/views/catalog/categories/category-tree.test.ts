import { expect, it } from 'vitest'

import { flattenCategoryTree, getValidParentOptions } from './category-tree'

const tree = [
  {
    id: 'A',
    name: 'A',
    slug: 'a',
    children: [{ id: 'B', name: 'B', slug: 'b', children: [{ id: 'C', name: 'C', slug: 'c' }] }]
  }
]

it('shows all three levels and excludes self and descendants from possible parents', () => {
  expect(flattenCategoryTree(tree).map(row => [row.item.id, row.level, row.parentName])).toEqual([
    ['A', 0, undefined],
    ['B', 1, 'A'],
    ['C', 2, 'B']
  ])
  expect(getValidParentOptions(tree, 'B').map(row => row.item.id)).toEqual(['A'])
})
