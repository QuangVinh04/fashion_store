'use client'

import { Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material'

import type { OrderItemResponse } from '@/libs/api/orders'
import { formatOrderMoney } from '@/views/orders/order-actions'

interface Props {
  items: OrderItemResponse[]
  currency: string
}

export function OrderItemsTable({ items, currency }: Props) {
  return (
    <TableContainer>
      <Table size='small' sx={{ minWidth: 550 }}>
        <TableHead>
          <TableRow>
            <TableCell>Sản phẩm</TableCell>
            <TableCell>Màu / Size</TableCell>
            <TableCell align='right'>Đơn giá</TableCell>
            <TableCell align='right'>Số lượng</TableCell>
            <TableCell align='right'>Thành tiền</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((item, index) => (
            <TableRow key={item.variantId + '-' + index}>
              <TableCell sx={{ fontWeight: 500 }}>{item.productName}</TableCell>
              <TableCell>
                {item.color || '—'} / {item.size || '—'}
              </TableCell>
              <TableCell align='right' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatOrderMoney(item.unitPrice, currency)}
              </TableCell>
              <TableCell align='right' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {item.quantity}
              </TableCell>
              <TableCell align='right' sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                {formatOrderMoney(item.lineTotal, currency)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
