// MUI Imports
import { useTheme } from '@mui/material/styles'

// Third-party Imports
import PerfectScrollbar from 'react-perfect-scrollbar'

// Type Imports
import type { VerticalMenuContextProps } from '@menu/components/vertical-menu/Menu'

// Component Imports
import { Menu, SubMenu, MenuItem, MenuSection } from '@menu/vertical-menu'

// Hook Imports
import useVerticalNav from '@menu/hooks/useVerticalNav'

// Styled Component Imports
import StyledVerticalNavExpandIcon from '@menu/styles/vertical/StyledVerticalNavExpandIcon'

// Style Imports
import menuItemStyles from '@core/styles/vertical/menuItemStyles'
import menuSectionStyles from '@core/styles/vertical/menuSectionStyles'

import { requiredPermissionsFor } from '@/configs/routePermissions'
import { useAuth } from '@/context/AuthContext'

type RenderExpandIconProps = {
  open?: boolean
  transitionDuration?: VerticalMenuContextProps['transitionDuration']
}

const RenderExpandIcon = ({ open, transitionDuration }: RenderExpandIconProps) => (
  <StyledVerticalNavExpandIcon open={open} transitionDuration={transitionDuration}>
    <i className='ri-arrow-right-s-line' />
  </StyledVerticalNavExpandIcon>
)

type CanViewFn = (path: string) => boolean

const SalesSection = ({ canView }: { canView: CanViewFn }) => {
  const canOrders = canView('/orders')
  const canReturns = canView('/returns')
  const canWallets = canView('/wallets')
  const canPromotions = canView('/promotions')

  if (!canOrders && !canReturns && !canWallets && !canPromotions) return null

  return (
    <MenuSection label='Bán hàng'>
      {(canOrders || canReturns) && (
        <SubMenu label='Đơn hàng' icon={<i className='ri-shopping-cart-2-line' />}>
          {canOrders && <MenuItem href='/orders'>Danh sách đơn hàng</MenuItem>}
          {canReturns && <MenuItem href='/returns'>Yêu cầu đổi trả</MenuItem>}
        </SubMenu>
      )}
      {canWallets && (
        <MenuItem href='/wallets' icon={<i className='ri-wallet-3-line' />}>
          Ví khách hàng
        </MenuItem>
      )}
      {canPromotions && (
        <MenuItem href='/promotions' icon={<i className='ri-coupon-3-line' />}>
          Khuyến mãi
        </MenuItem>
      )}
    </MenuSection>
  )
}

const ProductsSection = ({ canView }: { canView: CanViewFn }) => {
  if (!canView('/products')) return null

  return (
    <MenuSection label='Sản phẩm'>
      <SubMenu label='Sản phẩm' icon={<i className='ri-shirt-line' />}>
        <MenuItem href='/products'>Danh sách sản phẩm</MenuItem>
        <MenuItem href='/categories'>Danh mục</MenuItem>
        <MenuItem href='/sizes'>Kích cỡ</MenuItem>
        <MenuItem href='/brands'>Thương hiệu</MenuItem>
        <MenuItem href='/colors'>Màu sắc</MenuItem>
        <MenuItem href='/attributes'>Thuộc tính</MenuItem>
        <MenuItem href='/size-charts'>Bảng size</MenuItem>
        <MenuItem href='/media'>Thư viện ảnh</MenuItem>
      </SubMenu>
    </MenuSection>
  )
}

const CustomerSection = ({ canView }: { canView: CanViewFn }) => {
  const canCustomers = canView('/customers')
  const canReviews = canView('/reviews')

  if (!canCustomers && !canReviews) return null

  return (
    <MenuSection label='Khách hàng'>
      {canCustomers && (
        <MenuItem href='/customers' icon={<i className='ri-user-3-line' />}>
          Người dùng
        </MenuItem>
      )}
      {canReviews && (
        <MenuItem href='/reviews' icon={<i className='ri-star-line' />}>
          Đánh giá
        </MenuItem>
      )}
    </MenuSection>
  )
}

const AdminSection = ({ canView }: { canView: CanViewFn }) => {
  const canGroups = canView('/groups')
  const canAuditLogs = canView('/audit-logs')

  if (!canGroups && !canAuditLogs) return null

  return (
    <MenuSection label='Quản trị'>
      {canGroups && (
        <MenuItem href='/groups' icon={<i className='ri-shield-user-line' />}>
          Nhóm & phân quyền
        </MenuItem>
      )}
      {canAuditLogs && (
        <MenuItem href='/audit-logs' icon={<i className='ri-file-list-3-line' />}>
          Nhật ký thao tác
        </MenuItem>
      )}
    </MenuSection>
  )
}

const VerticalMenu = ({ scrollMenu }: { scrollMenu: (container: any, isPerfectScrollbar: boolean) => void }) => {
  // Hooks
  const theme = useTheme()
  const { isBreakpointReached, transitionDuration } = useVerticalNav()
  const { hasAnyPermission, canAccessBackoffice } = useAuth()

  const ScrollWrapper = isBreakpointReached ? 'div' : PerfectScrollbar

  const canView: CanViewFn = path => {
    const permissions = requiredPermissionsFor(path)

    if (!permissions || permissions.length === 0) {
      return canAccessBackoffice
    }

    return hasAnyPermission(permissions)
  }

  return (
    // eslint-disable-next-line lines-around-comment
    /* Custom scrollbar instead of browser scroll, remove if you want browser scroll only */
    <ScrollWrapper
      {...(isBreakpointReached
        ? {
            className: 'bs-full overflow-y-auto overflow-x-hidden',
            onScroll: container => scrollMenu(container, false)
          }
        : {
            options: { wheelPropagation: false, suppressScrollX: true },
            onScrollY: container => scrollMenu(container, true)
          })}
    >
      <Menu
        menuItemStyles={menuItemStyles(theme)}
        renderExpandIcon={({ open }) => <RenderExpandIcon open={open} transitionDuration={transitionDuration} />}
        renderExpandedMenuItemIcon={{ icon: <i className='ri-circle-line' /> }}
        menuSectionStyles={menuSectionStyles(theme)}
      >
        {canView('/') && (
          <MenuItem href='/' icon={<i className='ri-dashboard-line' />}>
            Tổng quan
          </MenuItem>
        )}

        <SalesSection canView={canView} />
        <ProductsSection canView={canView} />

        {canView('/inventory') && (
          <MenuSection label='Kho hàng'>
            <MenuItem href='/inventory' icon={<i className='ri-archive-line' />}>
              Tồn kho
            </MenuItem>
          </MenuSection>
        )}

        <CustomerSection canView={canView} />
        <AdminSection canView={canView} />
      </Menu>
    </ScrollWrapper>
  )
}

export default VerticalMenu
