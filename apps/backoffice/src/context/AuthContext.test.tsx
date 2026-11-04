import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, MockAuthProvider, useAuth } from './AuthContext'

vi.mock('next/navigation', () => ({
  usePathname: () => '/'
}))

let container: HTMLDivElement
let root: Root

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { assign: vi.fn() }
  })
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

async function settle() {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 50))
  })
}

async function renderWithClient(roles: string[], onReady: (auth: ReturnType<typeof useAuth>) => void) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false }
    }
  })

  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: {
          authenticated: true,
          userId: 'u-1',
          email: 'test@example.com',
          name: 'Test',
          roles
        }
      })
    )
  )

  function Consumer() {
    const auth = useAuth()

    if (!auth.isLoading) {
      onReady(auth)
    }

    return <div>{auth.isLoading ? 'loading' : 'ready'}</div>
  }

  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </QueryClientProvider>
    )
  })

  await settle()
  await settle()
}

describe('AuthContext RBAC permissions', () => {
  it('grants canAccessBackoffice and evaluates hasPermission when session has backoffice permissions', async () => {
    let capturedAuth: ReturnType<typeof useAuth> | null = null

    await renderWithClient(['ROLE_USER', 'order:read'], auth => {
      capturedAuth = auth
    })

    expect(capturedAuth).not.toBeNull()
    expect(capturedAuth!.canAccessBackoffice).toBe(true)
    expect(capturedAuth!.hasPermission('order:read')).toBe(true)
    expect(capturedAuth!.hasPermission('refund:approve')).toBe(false)
    expect(window.location.assign).not.toHaveBeenCalled()
  })

  it('denies access and redirects to /access-denied when roles only contain ROLE_ADMIN/ROLE_USER without backoffice permissions', async () => {
    let capturedAuth: ReturnType<typeof useAuth> | null = null

    await renderWithClient(['ROLE_ADMIN', 'ROLE_USER'], auth => {
      capturedAuth = auth
    })

    expect(capturedAuth).not.toBeNull()
    expect(capturedAuth!.canAccessBackoffice).toBe(false)
    expect(window.location.assign).toHaveBeenCalledWith('/access-denied')
  })

  it('denies access when roles only have internal-caller', async () => {
    let capturedAuth: ReturnType<typeof useAuth> | null = null

    await renderWithClient(['internal-caller'], auth => {
      capturedAuth = auth
    })

    expect(capturedAuth).not.toBeNull()
    expect(capturedAuth!.canAccessBackoffice).toBe(false)
    expect(window.location.assign).toHaveBeenCalledWith('/access-denied')
  })

  it('throws an error when useAuth is called outside AuthProvider', () => {
    function OrphanConsumer() {
      useAuth()

      return null
    }

    expect(() => {
      act(() => {
        root.render(<OrphanConsumer />)
      })
    }).toThrow('useAuth must be used within an AuthProvider')
  })

  it('provides permissions correctly through MockAuthProvider', () => {
    let capturedAuth: ReturnType<typeof useAuth> | null = null

    function MockConsumer() {
      capturedAuth = useAuth()

      return null
    }

    act(() => {
      root.render(
        <MockAuthProvider permissions={['order:read']}>
          <MockConsumer />
        </MockAuthProvider>
      )
    })

    expect(capturedAuth).not.toBeNull()
    expect(capturedAuth!.hasPermission('order:read')).toBe(true)
    expect(capturedAuth!.hasPermission('revenue:read')).toBe(false)
  })
})
