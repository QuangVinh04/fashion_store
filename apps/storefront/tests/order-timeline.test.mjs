import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';

const source = await readFile(new URL('../src/components/OrderActivityTimeline.tsx', import.meta.url), 'utf8');
const { code } = await transformWithEsbuild(source, 'timeline.tsx', { format: 'cjs', jsx: 'automatic' });
const module = { exports: {} };
const element = (type, props) => ({ type, props });
runInNewContext(code, { module, exports: module.exports, require(name) {
  if (name === 'react') return { useState: initial => [initial, () => {}] };
  if (name === 'react/jsx-runtime') return { jsx: element, jsxs: element };
  if (name === '../api/types') return { ORDER_LABEL: { CONFIRMED: 'Đã xác nhận', PACKED: 'Đã đóng gói', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy' } };
  return new Proxy({}, { get: () => 'icon' });
} });
function textOf(node) {
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(textOf).join(' ');
  return node?.props ? textOf(node.props.children) : '';
}
function render(entry) {
  return textOf(module.exports.default({ history: [{ id: 'h1', createdAt: '2026-10-05T02:40:20', ...entry }], loading: false, error: '', retry() {} }));
}

test('existing order history hides checkout and saga details without claiming COD was paid', () => {
  const created = render({ action: 'ORDER_CREATED', toStatus: 'PENDING', reason: 'Khách hàng tạo đơn hàng từ checkout' });
  assert.match(created, /Đã đặt hàng/);
  assert.match(created, /Đơn hàng của bạn đã được ghi nhận/);
  assert.doesNotMatch(created, /checkout/i);
  const confirmed = render({ action: 'ORDER_CONFIRMED', toStatus: 'CONFIRMED', reason: 'Saga hoàn tất thanh toán và giữ kho thành công' });
  assert.match(confirmed, /Đơn hàng đã được xác nhận và sẽ được shop chuẩn bị/);
  assert.doesNotMatch(confirmed, /saga|giữ kho|đã thanh toán/i);
});

test('shipment creation and return events take precedence over the order status', () => {
  const shipment = render({ action: 'SHIPMENT_CREATED', toStatus: 'PACKED', reason: 'Shipment created with tracking code: GHN123' });
  assert.match(shipment, /Đã tạo vận đơn/);
  assert.doesNotMatch(shipment, /Shipment created|GHN123/);
  assert.match(render({ action: 'RETURN_REJECTED', toStatus: 'DELIVERED' }), /Yêu cầu trả hàng chưa được chấp nhận/);
  assert.match(render({ action: 'RETURN_APPROVED', toStatus: 'RETURNED' }), /Yêu cầu trả hàng đã được chấp nhận/);
});

test('carrier and refund errors have customer descriptions rather than raw diagnostics', () => {
  assert.doesNotMatch(render({ action: 'GHN_WEBHOOK', toStatus: 'SHIPPING', reason: 'Status updated from GHN webhook: transporting' }), /webhook|transporting/i);
  const refund = render({ action: 'PAYMENT_REFUND_FAILED', toStatus: 'RETURNED', reason: 'Refund failed [PROVIDER_ERROR]: timeout — cần can thiệp thủ công' });
  assert.match(refund, /Chưa hoàn tiền thành công/);
  assert.doesNotMatch(refund, /PROVIDER_ERROR|timeout|can thiệp thủ công/);
});

test('unknown events and statuses never expose internal codes or raw reasons', () => {
  const text = render({ action: 'NEW_INTERNAL_EVENT', toStatus: 'INTERNAL_STATE', reason: 'Redis connection failed' });
  assert.match(text, /Đơn hàng đã được cập nhật/);
  assert.doesNotMatch(text, /NEW_INTERNAL_EVENT|INTERNAL_STATE|Redis/);
});
