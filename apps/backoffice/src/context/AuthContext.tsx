'use client'

import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react'

import { usePathname } from 'next/navigation'

import { useQuery } from '@tanstack/react-query'

export type SessionResponse = {
  authenticated: boolean
  userId: string | null
  email: string | null
  name: string | null
  roles: string[]
}

export type AuthContextType = {
  session: SessionResponse | null
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

const PUBLIC_PATHS = ['/access-denied']

async function fetchSession(): Promise<SessionResponse> {
  const res = await fetch('/bff/session', {
    headers: { Accept: 'application/json' },
    cache: 'no-store'
  })

  if (!res.ok) {
    return {
      authenticated: false,
      userId: null,
      email: null,
      name: null,
      roles: []
    }
  }

  const json = await res.json()


  // BFF wraps response in ApiResponse: { code: 1000, data: { authenticated, ... } }
  return json.data || json
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  const { data: session, isLoading } = useQuery<SessionResponse>({
    queryKey: ['bff-session'],
    queryFn: fetchSession,
    staleTime: 5 * 60 * 1000, // 5 mins
    retry: false
  })

  const isAuthenticated = !!session?.authenticated

  const isAdmin = useMemo(() => {
    return !!session?.roles?.includes('ROLE_ADMIN')
  }, [session?.roles])

  const login = () => {
    window.location.assign('/oauth2/authorization/keycloak')
  }

  const logout = () => {
    const form = document.createElement('form')

    form.method = 'POST'
    form.action = '/logout'

    const xsrf = document.cookie
      .split('; ')
      .find(part => part.startsWith('FS_BACKOFFICE_XSRF='))
      ?.split('=')[1]

    if (xsrf) {
      const input = document.createElement('input')

      input.type = 'hidden'
      input.name = '_csrf'
      input.value = decodeURIComponent(xsrf)
      form.appendChild(input)
    }

    document.body.appendChild(form)
    form.submit()
  }

  useEffect(() => {
    if (isLoading) return

    const isPublic = PUBLIC_PATHS.some(path => pathname?.startsWith(path))

    if (isPublic) return

    if (!isAuthenticated) {
      login()
    } else if (!isAdmin) {
      window.location.assign('/access-denied')
    }
  }, [isLoading, isAuthenticated, isAdmin, pathname])

  return (
    <AuthContext.Provider
      value={{
        session: session ?? null,
        isLoading,
        isAuthenticated,
        isAdmin,
        login,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }

  
return context
}
