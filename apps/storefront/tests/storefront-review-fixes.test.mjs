import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';
const helperSource=await readFile(new URL('../src/api/payment-return.ts',import.meta.url),'utf8');
const helperModule={exports:{}};
const helperCode=await transformWithEsbuild(helperSource.replace(/^import .*;\r?\n/gm,''),'payment-return.ts',{format:'cjs'});
runInNewContext(helperCode.code,{module:helperModule,exports:helperModule.exports});

async function render(file, overrides = {}, exportName = 'default', props = {}) {
  const source = await readFile(new URL(`../src/${file}`, import.meta.url), 'utf8');
  const { code } = await transformWithEsbuild(source, file, { format: 'cjs', jsx: 'automatic' });
  const module = { exports: {} };
  const updates = [];
  const element = (type, props) => ({ type, props });
  runInNewContext(code, {
    module, exports: module.exports, URLSearchParams, window: { scrollTo() {} }, sessionStorage: { getItem: () => null },
    require(name) {
      if (overrides[name]) return overrides[name];
      if(name.endsWith('/payment-return'))return helperModule.exports;
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
  const { tree } = await render('pages/PaymentReturn.tsx',{},'PaymentReturnContent');
  assert.doesNotMatch(text(tree), /THANH TOÁN THÀNH CÔNG/);
  assert.match(text(tree), /XÁC NHẬN THANH TOÁN/);
});

test('backend completed payment wins over a cancel URL', async () => {
  const { tree } = await render('pages/PaymentReturn.tsx', {
    'react-router': { Link: 'a', useLocation: () => ({ pathname: '/payment/payos/cancel' }), useSearchParams: () => [new URLSearchParams('orderId=o1&cancel=true')] },
    '../components/StoreUI': { useLoad: () => ({ data: { status: 'COMPLETED', provider: 'PAYOS', amount: 100 }, loading: false, error: '', refresh() {} }), Status: 'status' },
  },'PaymentReturnContent');
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

test('checkout cart load failure or loading is not presented as an empty cart', async () => {
  let retries = 0;
  const { tree: loadingTree } = await render('pages/Checkout.tsx', {
    '../context/CartContext': { useCart: () => ({ cart: null, count: 0, total: 0, loading: true, error: '' }) },
  });
  assert.doesNotMatch(text(loadingTree), /GIỎ HÀNG ĐANG TRỐNG/);
  assert.ok(nodes(loadingTree).some(node => node.type === 'status' && node.props.loading));

  const { tree: errorTree } = await render('pages/Checkout.tsx', {
    '../context/CartContext': { useCart: () => ({ cart: null, count: 0, total: 0, loading: false, error: 'Mất kết nối', refresh: () => { retries++; } }) },
  });
  assert.doesNotMatch(text(errorTree), /GIỎ HÀNG ĐANG TRỐNG/);
  assert.ok(nodes(errorTree).some(node => node.type === 'status' && node.props.error === 'Mất kết nối'));
  nodes(errorTree).find(node => node.type === 'status').props.retry();
  assert.equal(retries, 1);
});

async function wishlistAt(page, data, { loading = false, error = '', remove = async () => {} } = {}) {
  let stateIndex = 0;
  const pageChanges = [], effects = [];
  const rendered = await render('pages/Wishlist.tsx', {
    react: {
      useState: initial => {
        const index = stateIndex++;
        return [index === 0 ? page : initial, value => {
          if (index === 0) pageChanges.push(typeof value === 'function' ? value(page) : value);
        }];
      },
      useEffect: effect => effects.push(effect),
    },
    '../api/store': { store: { wishRemove: remove } },
    '../components/StoreUI': { useLoad: () => ({ data, loading, error, refresh: async () => {} }), Status: 'status' },
  });
  return { ...rendered, pageChanges, effects };
}

test('deleting the final wishlist item on page two goes back to page one', async () => {
  let deleted = '';
  const { tree, pageChanges } = await wishlistAt(1, {
    pageNo: 1, totalPage: 2, items: [{ productId: 'last', name: 'Shirt', basePrice: 100 }],
  }, { remove: async id => { deleted = id; } });
  const button = nodes(tree).find(node => node.type === 'button' && node.props.onClick && !node.props.disabled);
  assert.ok(button);
  await button.props.onClick();
  await new Promise(setImmediate);
  assert.equal(deleted, 'last');
  assert.deepEqual(pageChanges, [0]);
});

test('wishlist clamps an empty result to the last valid page and ignores stale results', async () => {
  const current = await wishlistAt(3, { pageNo: 3, totalPage: 2, items: [] });
  current.effects.forEach(effect => effect());
  assert.deepEqual(current.pageChanges, [1]);

  for (const loading of [true, false]) {
    const stale = await wishlistAt(1, { pageNo: 3, totalPage: 2, items: [] }, { loading });
    stale.effects.forEach(effect => effect());
    assert.deepEqual(stale.pageChanges, []);
  }
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

async function addressEditor(addresses) {
  const states = [];
  let cursor = 0;
  const store = { addresses() {}, provinces() {} };
  const overrides = {
    react: {
      useEffect() {},
      useState: initial => {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], value => { states[index] = typeof value === 'function' ? value(states[index]) : value; }];
      },
    },
    '../api/store': { store },
    '../components/StoreUI': { useLoad: load => ({ data: load === store.addresses ? addresses : [], loading: false, error: '', refresh() {} }), Status: 'status' },
  };
  return async () => {
    cursor = 0;
    return render('pages/AddressesPage.tsx', overrides);
  };
}

test('editing the current default address locks the default checkbox', async () => {
  const page = await addressEditor([{ id: 'home', recipientName: 'Home', isDefault: true, provinceId: 1, wardId: 2 }]);
  const initial = await page();
  nodes(initial.tree).find(node => node.type === 'button' && text(node).trim() === 'Sửa').props.onClick();
  const edited = await page();
  const checkbox = nodes(edited.tree).find(node => node.type === 'input' && node.props.type === 'checkbox');
  assert.equal(checkbox.props.checked, true);
  assert.equal(checkbox.props.disabled, true);
});

test('the first address is shown as default while another address may still be selected as default', async () => {
  const first = await (await addressEditor([]))();
  const firstCheckbox = nodes(first.tree).find(node => node.type === 'input' && node.props.type === 'checkbox');
  assert.equal(firstCheckbox.props.checked, true);
  assert.equal(firstCheckbox.props.disabled, true);

  const page = await addressEditor([{ id: 'office', recipientName: 'Office', isDefault: false, provinceId: 1, wardId: 2 }]);
  const initial = await page();
  nodes(initial.tree).find(node => node.type === 'button' && text(node).trim() === 'Sửa').props.onClick();
  const edited = await page();
  const checkbox = nodes(edited.tree).find(node => node.type === 'input' && node.props.type === 'checkbox');
  assert.equal(checkbox.props.checked, false);
  assert.equal(Boolean(checkbox.props.disabled), false);
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
    const button = nodes(tree).find(node => node.type === 'button' && (node.props['aria-label'] || text(node).trim()) === label);
    assert.equal(typeof button.props['aria-pressed'], 'boolean');
  }
  assert.equal(nodes(tree).find(node => node.type === 'input' && node.props.type === 'number').props['aria-label'], 'Số lượng sản phẩm');
});

// Reviews now live on their own route; exercise the same paging guarantees there.
async function reviewPager() {
  let productId = 'product-a', params = new URLSearchParams(), cursor = 0, effectCursor = 0;
  let loading = false, error = '', changed = false, retries = 0;
  const states = [], dependencies = [], cleanups = [], pendingEffects = [], calls = [];
  const store = {
    product: () => ({ id: productId, name: 'Shirt' }),
    reviews: (id, page) => {
      calls.push({ id, page }); retries++;
      if (loading) return new Promise(() => {});
      if (error) return Promise.reject(new Error(error));
      return Promise.resolve({ averageRating: 4, totalReviews: 13, ratingCounts: { 4: 13 }, reviews: {
        pageNo: page, totalPage: 2, pageSize: 10,
        items: Array.from({ length: page === 0 ? 10 : 3 }, (_, index) => ({
          id: `${id}-${page}-${index}`, rating: 4, comment: `Review ${page * 10 + index + 1}`,
          createdAt: '2026-10-01T12:00:00Z',
        })),
      } });
    },
  };
  const overrides = {
    react: {
      useState: initial => {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], value => {
          const next = typeof value === 'function' ? value(states[index]) : value;
          if (!Object.is(states[index], next)) changed = true;
          states[index] = next;
        }];
      },
      useEffect: (effect, deps) => {
        const index = effectCursor++;
        if (!dependencies[index] || deps.some((value, i) => !Object.is(value, dependencies[index][i]))) {
          dependencies[index] = deps;
          pendingEffects.push(() => { cleanups[index]?.(); cleanups[index] = effect(); });
        }
      },
    },
    'react-router': { Link: 'a', useParams: () => ({ id: productId }), useSearchParams: () => [params, next => { params = next; changed = true; }] },
    '../api/store': { store },
    '../api/client': { customerError: e => e.message },
    '../components/StoreUI': { Status: 'status', useLoad: load => ({ data: load(), loading: false, error: '', refresh() {} }) },
  };
  const draw = async () => {
    cursor = 0; effectCursor = 0; changed = false;
    const result = await render('pages/ProductReviews.tsx', overrides);
    pendingEffects.splice(0).forEach(effect => effect());
    await new Promise(resolve => setImmediate(resolve));
    return changed ? draw() : result.tree;
  };
  return { draw, calls, setProduct: id => { productId = id; params = new URLSearchParams(); },
    setLoading: value => { loading = value; }, setError: value => { error = value; }, retries: () => retries };
}

test('reviews after the first ten are reachable and a new product route starts on page one', async () => {
  const pager = await reviewPager();
  let tree = await pager.draw();
  assert.match(text(tree), /Review 10/);
  assert.equal(nodes(tree).find(node => node.props['aria-label'] === 'Trang đánh giá trước').props.disabled, true);
  nodes(tree).find(node => node.props['aria-label'] === 'Trang đánh giá sau').props.onClick();
  tree = await pager.draw();
  assert.match(text(tree), /Review 13/);
  assert.doesNotMatch(text(tree), /Review 10/);
  assert.deepEqual(pager.calls.at(-1), { id: 'product-a', page: 1 });
  assert.equal(nodes(tree).find(node => node.props['aria-label'] === 'Trang đánh giá sau').props.disabled, true);
  pager.setProduct('product-b');
  tree = await pager.draw();
  assert.deepEqual(pager.calls.at(-1), { id: 'product-b', page: 0 });
  assert.match(text(tree), /Review 10/);
});

test('review loading hides stale results and an error retains retry and a way back', async () => {
  const pager = await reviewPager();
  let tree = await pager.draw();
  pager.setLoading(true);
  nodes(tree).find(node => node.props['aria-label'] === 'Trang đánh giá sau').props.onClick();
  tree = await pager.draw();
  assert.doesNotMatch(text(tree), /Review 1/);
  assert.ok(nodes(tree).some(node => node.type === 'status' && node.props.loading));
  assert.ok(nodes(tree).some(node => node.props.to === '/products/product-a'));

  pager.setLoading(false); pager.setError('Unable to load reviews'); pager.setProduct('product-b');
  tree = await pager.draw();
  const status = nodes(tree).find(node => node.type === 'status' && node.props.error);
  assert.ok(status);
  assert.doesNotMatch(text(tree), /Chưa có đánh giá/);
  const before = pager.retries();
  status.props.retry();
  await pager.draw();
  assert.equal(pager.retries(), before + 1);
});
