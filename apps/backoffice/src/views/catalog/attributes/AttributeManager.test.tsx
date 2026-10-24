import { act } from 'react'

import { createRoot } from 'react-dom/client'
import { Simulate } from 'react-dom/test-utils'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'

import AttributeManager from './AttributeManager'

const mocks = vi.hoisted(() => ({ attributes: vi.fn(), options: vi.fn() }))

vi.mock('@/hooks/useListQueryState', () => ({
  default: () => ({ page: 0, size: 20, filters: {}, setPage: vi.fn(), setSize: vi.fn(), setFilters: vi.fn() }),
  resolveListPage: (page: number) => page
}))
vi.mock('@/libs/api/catalog-metadata', () => ({
  metadataApi: {
    attributes: mocks.attributes,
    options: mocks.options,
    saveAttribute: vi.fn(),
    deleteAttribute: vi.fn(),
    saveOption: vi.fn(),
    deleteOption: vi.fn()
  }
}))

const material = {
  id: 'material',
  name: 'Material',
  displayName: 'Chất liệu',
  code: 'MATERIAL',
  type: 'MULTI_SELECT',
  published: true,
  filterable: true
}

const origin = { id: 'origin', name: 'Origin', displayName: 'Xuất xứ', code: 'ORIGIN', type: 'TEXT', published: false }

let dispose: (() => void) | undefined

afterEach(() => {
  dispose?.()
  vi.clearAllMocks()
})

async function mount() {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  mocks.attributes.mockResolvedValue({ items: [material, origin], totalPage: 1, pageNo: 0, pageSize: 20 })
  mocks.options.mockResolvedValue([{ id: 'cotton', value: 'Cotton', published: true }])

  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  document.body.append(container)

  dispose = () => {
    act(() => root.unmount())
    client.clear()
    container.remove()
  }

  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>
        <AttributeManager />
      </QueryClientProvider>
    )
  )
  await settle()
  await settle()

  return container
}

async function settle() {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 80))
  })
}

it('shows 4 columns (Thuộc tính, Số giá trị, Trạng thái, Thao tác) and navigates to dedicated values page', async () => {
  const container = await mount()

  // 1. Kiểm tra 4 cột chuẩn Option A
  expect(Array.from(container.querySelectorAll('thead th')).map(cell => cell.textContent)).toEqual([
    'Thuộc tính',
    'Số giá trị',
    'Trạng thái',
    'Thao tác'
  ])

  // 2. Kiểm tra hiển thị dòng: Chất liệu và Xuất xứ
  const rows = container.querySelectorAll('tbody tr')

  expect(rows).toHaveLength(2)
  expect(rows[0].textContent).toContain('Chất liệu')
  expect(rows[0].textContent).toContain('MATERIAL')
  expect(rows[0].textContent).toContain('1 giá trị')
  expect(mocks.options).toHaveBeenCalledWith('material')

  // Thuộc tính TEXT không có tùy chọn trả về "—" và không gọi API options
  expect(rows[1].textContent).toContain('Xuất xứ')
  expect(rows[1].textContent).toContain('—')
  expect(mocks.options.mock.calls.every(([id]) => id === 'material')).toBe(true)

  // 3. Bấm vào số giá trị mở Trang 2 (Quản lý giá trị tùy chọn con)
  act(() => Simulate.click(container.querySelector('[aria-label="Xem chi tiết Chất liệu"]')!))
  await settle()
  await settle()

  expect(container.textContent).toContain('Giá trị tùy chọn · Chất liệu')
  expect(container.textContent).toContain('Cotton')
  expect(container.querySelector('[aria-label="Quay lại danh sách thuộc tính"]')).not.toBeNull()

  // 4. Bấm nút quay lại trên header quay về Trang 1 danh sách thuộc tính
  act(() => Simulate.click(container.querySelector('[aria-label="Quay lại danh sách thuộc tính"]')!))
  await settle()

  expect(container.textContent).toContain('Thuộc tính sản phẩm')
  expect(container.querySelectorAll('tbody tr')).toHaveLength(2)
})

it('opens edit attribute popup modal from the action button', async () => {
  const container = await mount()

  // Bấm icon Sửa trên dòng thuộc tính
  act(() => Simulate.click(container.querySelector('[aria-label="Sửa Chất liệu"]')!))
  await settle()

  // Popup modal mở ra
  const dialog = document.querySelector('.MuiDialog-root')

  expect(dialog).not.toBeNull()
  expect(dialog?.textContent).toContain('Sửa thuộc tính')
  expect(dialog?.textContent).toContain('Material')

  // Kiểm tra các trường input đã điền đúng thông tin
  const inputs = dialog?.querySelectorAll('input')
  const values = Array.from(inputs || []).map(input => (input as HTMLInputElement).value)

  expect(values).toContain('Material')
  expect(values).toContain('Chất liệu')
})

it('navigates to values page for TEXT attributes via action button and value count button', async () => {
  const container = await mount()

  // 1. Dòng Xuất xứ (type TEXT) có nút Quản lý giá trị trong cột Thao tác
  const manageOriginBtn = container.querySelector('[aria-label="Quản lý giá trị Xuất xứ"]')

  expect(manageOriginBtn).not.toBeNull()

  // Bấm vào nút Quản lý giá trị để mở Trang 2
  act(() => Simulate.click(manageOriginBtn!))
  await settle()
  await settle()

  expect(container.textContent).toContain('Giá trị tùy chọn · Xuất xứ')
  expect(container.textContent).toContain('Văn bản (Text)')
  expect(container.querySelector('[aria-label="Quay lại danh sách thuộc tính"]')).not.toBeNull()

  // Quay lại danh sách
  act(() => Simulate.click(container.querySelector('[aria-label="Quay lại danh sách thuộc tính"]')!))
  await settle()

  // 2. Bấm vào nút "—" ở cột Số giá trị của Xuất xứ cũng mở Trang 2
  const originCountBtn = container.querySelector('[aria-label="Xem chi tiết Xuất xứ"]')

  expect(originCountBtn).not.toBeNull()
  act(() => Simulate.click(originCountBtn!))
  await settle()
  await settle()

  expect(container.textContent).toContain('Giá trị tùy chọn · Xuất xứ')
})

