import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';

const client = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8');
const resources = await readFile(new URL('../src/api/order-resources.ts', import.meta.url), 'utf8');
const { code } = await transformWithEsbuild(client + '\n' + resources.replace(/^import .*;\r?\n/gm, ''), 'resources.ts', { format: 'cjs' });
const module = { exports: {} };
runInNewContext(code, { module, exports: module.exports, setTimeout, clearTimeout, DOMException });
const { ApiError, loadOrderPayment, loadOrderShipment } = module.exports;

test('payment retries only the expected missing record and returns the created payment', async () => {
  let calls = 0;
  const payment = { id: 'payment', status: 'COD_PENDING' };
  assert.equal(await loadOrderPayment(async () => {
    if (++calls < 3) throw new ApiError(404, 5001, 'Payment not found');
    return payment;
  }, new AbortController().signal, 0), payment);
  assert.equal(calls, 3);
});

test('payment retries are bounded when no record is created', async () => {
  let calls = 0;
  assert.equal(await loadOrderPayment(async () => {
    calls++;
    throw new ApiError(404, 5001, 'Payment not found');
  }, new AbortController().signal, 0), null);
  assert.equal(calls, 16);
});

test('unexpected API and network errors are not treated as missing payment', async () => {
  for (const [status, code] of [[401, 5001], [500, 5001], [404, 9007], [0, undefined]]) {
    let calls = 0;
    const error = new ApiError(status, code, 'failure');
    await assert.rejects(loadOrderPayment(async () => { calls++; throw error; }, new AbortController().signal, 0), e => e === error);
    assert.equal(calls, 1);
  }
});

test('leaving the page cancels payment retries', async () => {
  const controller = new AbortController();
  let calls = 0;
  await assert.rejects(loadOrderPayment(async () => {
    calls++;
    controller.abort();
    throw new ApiError(404, 5001, 'Payment not found');
  }, controller.signal, 0), { name: 'AbortError' });
  assert.equal(calls, 1);
});

test('no tracking code means no shipment API request', async () => {
  let calls = 0;
  assert.equal(await loadOrderShipment(undefined, async () => { calls++; }), null);
  assert.equal(calls, 0);
});

test('shipment missing record is an empty state; other failures remain errors', async () => {
  assert.equal(await loadOrderShipment('GHN123', async () => { throw new ApiError(404, 9007, 'Resource not found'); }), null);
  const error = new ApiError(500, 9007, 'Unavailable');
  await assert.rejects(loadOrderShipment('GHN123', async () => { throw error; }), e => e === error);
  const shipment = { trackingCode: 'GHN123' };
  assert.equal(await loadOrderShipment('GHN123', async () => shipment), shipment);
});

const pageSource = await readFile(new URL('../src/pages/OrderDetail.tsx', import.meta.url), 'utf8');
const pageCode = (await transformWithEsbuild(pageSource, 'OrderDetail.tsx', { format: 'cjs', jsx: 'automatic' })).code;
function mountOrder(order) {
  const effects = [];
  const calls = [];
  const updates = [];
  let loads = 0;
  const pageModule = { exports: {} };
  const element = (type, props) => ({ type, props });
  runInNewContext(pageCode, {
    module: pageModule, exports: pageModule.exports, AbortController,
    window: { setInterval: () => 1, clearInterval: () => {} },
    require(name) {
      if (name === 'react') return {
        useState: initial => [initial, value => updates.push(value)],
        useEffect: effect => effects.push(effect),
      };
      if (name === 'react/jsx-runtime') return { jsx: element, jsxs: element };
      if (name === 'react-router') return { Link: 'a', useParams: () => ({ id: 'order-1' }) };
      if (name === '../api/store') return { store: {
        payment: async id => { calls.push(['payment', id]); return { status: 'COD_PENDING' }; },
        orderShipment: async id => { calls.push(['shipment', id]); throw new ApiError(404, 9007, 'Resource not found'); },
      } };
      if (name === '../api/client') return { money: () => '0 đ' };
      if (name === '../api/order-resources') return { loadOrderPayment, loadOrderShipment };
      if (name === '../components/StoreUI') return {
        useLoad: () => ({ data: loads++ === 0 ? order : [], refresh: async () => {} }),
        Status: 'status', StatusBadge: 'badge',
      };
      return new Proxy({}, { get: () => 'component' });
    },
  });
  pageModule.exports.default();
  const cleanups = effects.map(effect => effect());
  return { calls, updates, cleanup: () => cleanups.forEach(fn => fn?.()) };
}

test('pending COD page waits for authorization without payment or shipment requests', async () => {
  const page = mountOrder({ id: 'order-1', status: 'PENDING', paymentMethod: 'COD', items: [] });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(page.calls, []);
  page.cleanup();
});

test('confirmed page loads payment but does not request shipment until a tracking code exists', async () => {
  const page = mountOrder({ id: 'order-1', status: 'CONFIRMED', paymentId: 'p1', items: [] });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(page.calls, [['payment', 'order-1']]);
  page.cleanup();
  const shipped = mountOrder({ id: 'order-1', status: 'PACKED', paymentId: 'p1', trackingCode: 'GHN123', items: [] });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(shipped.calls, [['payment', 'order-1'], ['shipment', 'order-1']]);
  assert.equal(shipped.updates.some(value => typeof value === 'string' && value.includes('Không thể tải')), false);
  shipped.cleanup();
});
