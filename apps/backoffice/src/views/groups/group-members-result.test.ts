import { describe, expect, it } from 'vitest'

import { aggregateMemberChanges, chunkUserIds } from './group-members-result'

describe('chunkUserIds', () => {
  it('splits array of userIds into chunks of up to 100', () => {
    const userIds = Array.from({ length: 250 }, (_, i) => `user-${i + 1}`)
    const chunks = chunkUserIds(userIds, 100)

    expect(chunks).toHaveLength(3)
    expect(chunks[0]).toHaveLength(100)
    expect(chunks[1]).toHaveLength(100)
    expect(chunks[2]).toHaveLength(50)
  })

  it('keeps array in single chunk when length <= chunkSize', () => {
    const userIds = ['u1', 'u2', 'u3']
    const chunks = chunkUserIds(userIds, 100)

    expect(chunks).toEqual([['u1', 'u2', 'u3']])
  })
})

describe('aggregateMemberChanges', () => {
  it('formats clean success notice when there are no failures (add)', () => {
    const summary = aggregateMemberChanges(
      [{ succeededUserIds: ['u1', 'u2'], failures: [], notLoggedOutUserIds: [] }],
      'add'
    )

    expect(summary.successNotice).toBe('Đã thêm thành công 2 thành viên vào nhóm.')
    expect(summary.failureAlert).toBeNull()
    expect(summary.logoutWarning).toBeNull()
    expect(summary.failures).toHaveLength(0)
  })

  it('formats clean success notice when there are no failures (remove)', () => {
    const summary = aggregateMemberChanges(
      [{ succeededUserIds: ['u1'], failures: [], notLoggedOutUserIds: [] }],
      'remove'
    )

    expect(summary.successNotice).toBe('Đã xóa thành công 1 thành viên khỏi nhóm.')
    expect(summary.failureAlert).toBeNull()
  })

  it('formats partial failure alert and maps email when available', () => {
    const summary = aggregateMemberChanges(
      [
        {
          succeededUserIds: ['u1'],
          failures: [{ userId: 'u2', code: 2035, message: 'Không thể tự rời nhóm Admin của chính mình' }],
          notLoggedOutUserIds: []
        }
      ],
      'remove',
      { 'u2': 'admin@example.com' }
    )

    expect(summary.successNotice).toBeNull()
    expect(summary.failureAlert).toBe('1 thành công, 1 thất bại.')
    expect(summary.failures).toEqual([
      {
        userId: 'u2',
        email: 'admin@example.com',
        code: 2035,
        message: 'Không thể tự rời nhóm Admin của chính mình'
      }
    ])
  })

  it('formats total failure alert when succeededUserIds is empty', () => {
    const summary = aggregateMemberChanges(
      [
        {
          succeededUserIds: [],
          failures: [{ userId: 'u1', code: 2034, message: 'Phải còn ít nhất một quản trị viên' }],
          notLoggedOutUserIds: []
        }
      ],
      'remove'
    )

    expect(summary.failureAlert).toBe('1 thất bại.')
    expect(summary.failures[0].email).toBeNull()
  })

  it('surfaces changeWarning when notLoggedOutUserIds is present', () => {
    const summary = aggregateMemberChanges(
      [
        {
          succeededUserIds: ['u1', 'u2'],
          failures: [],
          notLoggedOutUserIds: ['u1']
        }
      ],
      'add'
    )

    expect(summary.logoutWarning).toContain('1 người dùng chưa bị đăng xuất')
  })
})
