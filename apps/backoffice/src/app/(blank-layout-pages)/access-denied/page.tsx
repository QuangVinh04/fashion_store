'use client'

// React Imports
import { useCallback } from 'react'

// MUI Imports
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'

// Component Imports
import Illustrations from '@components/Illustrations'

const AccessDenied = () => {
  const handleLogout = useCallback(() => {
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
  }, [])

  return (
    <div className='flex items-center justify-center min-bs-[100dvh] relative p-6 overflow-x-hidden'>
      <div className='flex items-center flex-col text-center gap-10'>
        <div className='flex flex-col gap-2 is-[90vw] sm:is-[unset]'>
          <Typography className='font-medium text-8xl' color='error'>
            403
          </Typography>
          <Typography variant='h4'>Truy cập bị từ chối ⛔</Typography>
          <Typography>Tài khoản hiện tại không có quyền quản trị hệ thống.</Typography>
        </div>
        <img
          alt='access-denied-illustration'
          src='/images/illustrations/characters/5.png'
          className='object-cover bs-[300px] md:bs-[350px]'
        />
        <Button variant='contained' color='error' onClick={handleLogout}>
          Đăng xuất tài khoản
        </Button>
      </div>
      <Illustrations maskImg={{ src: '/images/pages/misc-mask-light.png' }} />
    </div>
  )
}

export default AccessDenied
