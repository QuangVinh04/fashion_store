import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';

async function render(file, overrides = {}, exportName = 'default', props = {}) {
  const source = await readFile(new URL(`../src/${file}`, import.meta.url), 'utf8');
  const { code } = await transformWithEsbuild(source, file, { format: 'cjs', jsx: 'automatic' });
  const module = { exports: {} };
  const updates = [];
  const element = (type, props) => ({ type, props });
  runInNewContext(code, {
    module, exports: module.exports, sessionStorage: { getItem: () => null },
    require(name) {
      if (overrides[name]) return overrides[name];
      if (name === 'react') return { useEffect() {}, useState: value => [value, next => updates.push(next)], useMemo: fn => fn(), useRef: value => ({ current: value }) };
      if (name === 'react/jsx-runtime') return { jsx: element, jsxs: element };
      if (name === 'react-router') return { Link: 'a', useNavigate: () => () => {}, useLocation: () => ({ pathname: '/payment/payos/success' }), useSearchParams: () => [new URLSearchParams('orderId=o1')] };
      if (name.endsWith('/StoreUI')) return { useLoad: () => ({ data: null, loading: false, error: '', refresh() {} }), Status: 'status', ProductCard: 'card', SkeletonCard: 'skeleton' };
      if (name.endsWith('/AuthContext')) return { useAuth: () => ({ isLoggedIn: true, user: {}, login() {} }) };
      if (name.endsWith('/client')) return { money: value => String(value), media: () => '' };
      return new Proxy({}, { get: () => 'component' });
    },
  });
  return { tree: module.exports[exportName](props), updates };
}
function nodes(node) {
  if (Array.isArray(node)) return node.flatMap(nodes);
  if (!node?.props) return [];
  return [node, ...nodes(node.props.children)];
}
function text(node) {
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(text).join(' ');
  return node?.props ? text(node.props.children) : '';
}

test('success URL cannot claim payment success without backend confirmation', async () => {
  const { tree } = await render('pages/PaymentReturn.tsx');
  assert.doesNotMatch(text(tree), /THANH TOÁN THÀNH CÔNG/);
  assert.match(text(tree), /XÁC NHẬN THANH TOÁN/);
});

test('backend completed payment wins over a cancel URL', async () => {
  const { tree } = await render('pages/PaymentReturn.tsx', {
    'react-router': { Link: 'a', useLocation: () => ({ pathname: '/payment/payos/cancel' }), useSearchParams: () => [new URLSearchParams('orderId=o1&cancel=true')] },
    '../components/StoreUI': { useLoad: () => ({ data: { status: 'COMPLETED', provider: 'PAYOS', amount: 100 }, loading: false, error: '', refresh() {} }), Status: 'status' },
  });
  assert.match(text(tree), /THANH TOÁN THÀNH CÔNG/);
  assert.doesNotMatch(text(tree), /GIAO DỊCH KHÔNG THÀNH CÔNG/);
});

test('cart load failure is not presented as an empty cart', async () => {
  const { tree } = await render('pages/Cart.tsx', {
    '../context/CartContext': { useCart: () => ({ cart: null, count: 0, total: 0, loading: false, error: 'Unavailable' }) },
  });
  assert.doesNotMatch(text(tree), /GIỎ HÀNG CỦA BẠN ĐANG TRỐNG/);
  assert.ok(nodes(tree).some(node => node.type === 'status' && node.props.error));
});

test('newsletter without a subscription API does not promise a successful subscription', async () => {
  const { tree } = await render('pages/Home.tsx');
  assert.match(text(tree), /nhận thông tin qua email hiện chưa/);
  assert.equal(nodes(tree).some(node => node.type === 'form'), false);
  assert.doesNotMatch(text(tree), /2025/);
});

test('wishlist failure is caught and does not change the saved state', async () => {
  const { tree, updates } = await render('components/StoreUI.tsx', {
    '../api/store': { store: { wishAdd: async () => { throw new Error('Internal failure'); } } },
  }, 'ProductCard', { product: { id: 'p1', name: 'Áo', price: 100 } });
  const button = nodes(tree).find(node => node.type === 'button' && node.props['aria-label'] === 'Yêu thích');
  await button.props.onClick({ preventDefault() {}, stopPropagation() {} });
  assert.ok(updates.some(value => typeof value === 'string' && value.includes('yêu thích')));
  assert.equal(updates.filter(value => value === true).length, 1); // busy only, not liked
});

test('address province failure has a retry action and disables the selector', async () => {
  let load = 0;
  let retried = false;
  const { tree } = await render('pages/AddressesPage.tsx', {
    '../api/store': { store: {} },
    '../components/StoreUI': {
      useLoad: () => load++ === 0
        ? { data: [], loading: false, error: '' }
        : { data: null, loading: false, error: 'Internal failure', refresh: () => { retried = true; } },
      Status: 'status', PageTitle: 'h1',
    },
  });
  assert.match(text(tree), /Không tải được danh sách tỉnh\/thành phố/);
  assert.doesNotMatch(text(tree), /Internal failure/);
  assert.equal(nodes(tree).find(node => node.props.id === 'province').props.disabled, true);
  nodes(tree).find(node => node.type === 'button' && text(node) === 'Thử lại').props.onClick();
  assert.equal(retried, true);
});

test('ward load clears old options and records a recoverable failure', async () => {
  const effects = [];
  const updates = [];
  let state = 0;
  let load = 0;
  await render('pages/AddressesPage.tsx', {
    react: {
      useState(initial) { const index = state++; return [index === 4 ? '1' : initial, value => updates.push([index, value])]; },
      useEffect: effect => effects.push(effect),
    },
    '../api/store': { store: { wards: async () => { throw new Error('Connection refused'); } } },
    '../components/StoreUI': {
      useLoad: () => ({ data: load++ === 0 ? [] : [{ code: '1', name: 'Tỉnh' }], loading: false, error: '' }),
      Status: 'status', PageTitle: 'h1',
    },
  });
  const cleanup = effects[0]();
  await new Promise(resolve => setImmediate(resolve));
  assert.ok(updates.some(([index, value]) => index === 0 && Array.isArray(value) && value.length === 0));
  assert.ok(updates.some(([index, value]) => index === 2 && value.includes('Không tải được')));
  assert.ok(updates.some(([index, value]) => index === 1 && value === false));
  cleanup();
});

test('product color and size buttons expose selected state and quantity has a name', async () => {
  let load = 0;
  const product = { id: 'p1', name: 'Áo', price: 100, images: [], variants: [{ id: 'v1', active: true, color: 'Đen', size: 'M' }] };
  const { tree } = await render('pages/ProductDetail.tsx', {
    'react-router': { Link: 'a', useParams: () => ({ id: 'p1' }), useNavigate: () => () => {} },
    '../context/CartContext': { useCart: () => ({ addItem() {} }) },
    '../components/StoreUI': {
      useLoad: () => ({ data: load++ === 0 ? product : null, loading: false, error: '' }), Status: 'status',
    },
  });
  for (const label of ['Đen', 'M']) {
    const button = nodes(tree).find(node => node.type === 'button' && text(node).trim() === label);
    assert.equal(typeof button.props['aria-pressed'], 'boolean');
  }
  assert.equal(nodes(tree).find(node => node.type === 'input' && node.props.type === 'number').props['aria-label'], 'Số lượng sản phẩm');
});
