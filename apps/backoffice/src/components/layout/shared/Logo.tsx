'use client'

// React Imports
import type { CSSProperties } from 'react'

// Third-party Imports
import styled from '@emotion/styled'

// Config Imports
import themeConfig from '@configs/themeConfig'

type LogoTextProps = {
  color?: CSSProperties['color']
}

const LogoText = styled.span<LogoTextProps>`
  color: ${({ color }) => color ?? 'var(--mui-palette-text-primary)'};
  font-size: 1.125rem;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  margin-inline-start: 10px;
`

const LinoLogoMark = () => (
  <svg width='26' height='26' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'>
    <rect width='24' height='24' rx='6' fill='var(--mui-palette-primary-main)' />
    <path d='M8 6.5V16.5H16.5' stroke='#FFFFFF' strokeWidth='2.4' strokeLinecap='round' strokeLinejoin='round' />
  </svg>
)

const Logo = ({ color }: { color?: CSSProperties['color'] }) => {
  return (
    <div className='flex items-center min-bs-[24px]'>
      <LinoLogoMark />
      <LogoText color={color}>{themeConfig.templateName}</LogoText>
    </div>
  )
}

export default Logo
