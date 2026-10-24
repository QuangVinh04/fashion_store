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

type RenderExpandIconProps = {
  open?: boolean
  transitionDuration?: VerticalMenuContextProps['transitionDuration']
}

const RenderExpandIcon = ({ open, transitionDuration }: RenderExpandIconProps) => (
  <StyledVerticalNavExpandIcon open={open} transitionDuration={transitionDuration}>
    <i className='ri-arrow-right-s-line' />
  </StyledVerticalNavExpandIcon>
)

const VerticalMenu = ({ scrollMenu }: { scrollMenu: (container: any, isPerfectScrollbar: boolean) => void }) => {
  // Hooks
  const theme = useTheme()
  const { isBreakpointReached, transitionDuration } = useVerticalNav()

  const ScrollWrapper = isBreakpointReached ? 'div' : PerfectScrollbar

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
        <MenuItem href='/' icon={<i className='ri-dashboard-line' />}>
          Tổng quan
        </MenuItem>

        <MenuSection label='Bán hàng'>
          <SubMenu label='Đơn hàng' icon={<i className='ri-shopping-cart-2-line' />}>
            <MenuItem href='/orders'>Danh sách đơn hàng</MenuItem>
            <MenuItem href='/returns'>Yêu cầu đổi trả</MenuItem>
          </SubMenu>
          <MenuItem href='/wallets' icon={<i className='ri-wallet-3-line' />}>
            Ví khách hàng
          </MenuItem>
          <MenuItem href='/promotions' icon={<i className='ri-coupon-3-line' />}>
            Khuyến mãi
          </MenuItem>
        </MenuSection>

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

        <MenuSection label='Kho hàng'>
          <MenuItem href='/inventory' icon={<i className='ri-archive-line' />}>
            Tồn kho
          </MenuItem>
        </MenuSection>

        <MenuSection label='Khách hàng'>
          <MenuItem href='/customers' icon={<i className='ri-user-3-line' />}>
            Người dùng
          </MenuItem>
          <MenuItem href='/reviews' icon={<i className='ri-star-line' />}>
            Đánh giá
          </MenuItem>
        </MenuSection>
      </Menu>
    </ScrollWrapper>
  )
}

export default VerticalMenu
