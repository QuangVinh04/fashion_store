import { describe, expect, it } from 'vitest'

import { validateStaffForm } from './staff-form'

describe('validateStaffForm', () => {
  it('rejects empty fields and empty groupIds', () => {
    const errors = validateStaffForm({
      email: '',
      firstName: '',
      lastName: '',
      temporaryPassword: '',
      groupIds: []
    })

    expect(errors.email).toBeDefined()
    expect(errors.firstName).toBeDefined()
    expect(errors.lastName).toBeDefined()
    expect(errors.temporaryPassword).toBeDefined()
    expect(errors.groupIds).toBeDefined()
  })

  it('rejects invalid email format', () => {
    const errors = validateStaffForm({
      email: 'not-an-email',
      firstName: 'An',
      lastName: 'Nguyen',
      temporaryPassword: 'Password123!',
      groupIds: ['g1']
    })

    expect(errors.email).toBeDefined()
    expect(errors.firstName).toBeUndefined()
  })

  it('passes when all required fields are valid', () => {
    const errors = validateStaffForm({
      email: 'staff@fashionstore.vn',
      firstName: 'An',
      lastName: 'Nguyen',
      temporaryPassword: 'Password123!',
      groupIds: ['g1']
    })

    expect(Object.keys(errors)).toHaveLength(0)
  })
})
