import { expect, it } from 'vitest'

import { validateAttributeValues } from './attribute-validation'

it('reports blank, oversized and invalid numeric values at their real payload indexes', () => {
  const rows = [
    { attributeId: 'a', value: 'Cotton' },
    { attributeId: 'a', value: '' },
    { attributeId: 'b', value: 'wrong' },
    { attributeId: 'c', value: 'x'.repeat(256) }
  ]

  const errors = validateAttributeValues(rows, [{ id: 'b', name: 'Weight', published: true, type: 'NUMBER' }])

  expect(Object.keys(errors)).toEqual(['attributes[1].value', 'attributes[2].value', 'attributes[3].value'])
})
