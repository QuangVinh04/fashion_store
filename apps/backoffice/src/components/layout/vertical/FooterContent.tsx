'use client'

// Third-party Imports
import classnames from 'classnames'

// Hook Imports
import useVerticalNav from '@menu/hooks/useVerticalNav'

// Util Imports
import { verticalLayoutClasses } from '@layouts/utils/layoutClasses'

const FooterContent = () => {
  // Hooks
  const { isBreakpointReached } = useVerticalNav()

  return (
    <div
      className={classnames(verticalLayoutClasses.footerContent, 'flex items-center justify-between flex-wrap gap-4 text-sm')}
    >
      <p>
        <span>{`© ${new Date().getFullYear()} `}</span>
        <span className='font-semibold text-textPrimary'>Fashion Store</span>
        <span className='text-textSecondary'> - Hệ thống quản trị bán hàng buôn & lẻ</span>
      </p>
      {!isBreakpointReached && (
        <div className='flex items-center gap-4 text-textSecondary text-sm'>
          <span className='flex items-center gap-1.5'>
            <span className='inline-block w-2 h-2 rounded-full bg-success' />
            Hệ thống ổn định
          </span>
          <span>v1.0.0</span>
        </div>
      )}
    </div>
  )
}

export default FooterContent
