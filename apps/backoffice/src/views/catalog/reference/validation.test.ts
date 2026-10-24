import { describe, expect, it } from 'vitest'

import { validateReference } from './validation'

describe('reference validation', () => {
  it('accepts six-digit colors and rejects short colors and negative order', () => {
    expect(validateReference('colors', { name: 'Đen', colorHex: '#000000', displayOrder: 0 })).toEqual({})
    expect(validateReference('colors', { name: 'Đen', colorHex: '#123', displayOrder: -1 })).toHaveProperty('colorHex')
    expect(validateReference('colors', { name: 'Đen', colorHex: '#123', displayOrder: -1 })).toHaveProperty(
      'displayOrder'
    )
  })

  it('enforces backend name limits and required names', () => {
    expect(validateReference('sizes', { name: ' '.repeat(2) })).toHaveProperty('name')
    expect(validateReference('sizes', { name: 'S'.repeat(51) })).toHaveProperty('name')
  })
})
