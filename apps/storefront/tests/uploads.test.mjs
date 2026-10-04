import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';

// Run the real API client and store with browser globals and transport stubbed.
const client = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8');
const store = (await readFile(new URL('../src/api/store.ts', import.meta.url), 'utf8'))
  .replace(/^import .* from '\.\/client';\r?\n/m, '')
  .replace(/^import type .*;\r?\n/m, '');
const { code } = await transformWithEsbuild(client + '\n' + store, 'uploads.ts', { format: 'cjs' });
function apiStore(fetch) {
  const module = { exports: {} };
  runInNewContext(code, { module, exports: module.exports, fetch, Headers, Response, FormData, URLSearchParams,
    document: { cookie: 'FS_STOREFRONT_XSRF=test-token' } });
  return module.exports.store;
}
const envelope = data => Response.json({ code: 1000, data });
const file = new File(['image'], 'avatar.png', { type: 'image/png' });
const signed = { mediaId: 'm1', uploadUrl: 'https://storage.test/put', contentType: 'image/png',
  uploadHeaders: { 'Content-Type': 'image/png', 'If-None-Match': '*' } };

test('avatar upload waits for Catalog complete and returns its URL with signed PUT headers', async () => {
  const calls = [];
  let complete;
  let completedRequest;
  const ready = new Promise(resolve => { completedRequest = resolve; });
  const completed = new Promise(resolve => { complete = resolve; });
  const store = apiStore(async (url, init) => {
    calls.push([url, init]);
    if (url.endsWith('/presign')) return envelope(signed);
    if (url === signed.uploadUrl) return new Response(null, { status: 200 });
    completedRequest();
    return completed;
  });
  let settled = false;
  const upload = store.uploadAvatar(file).then(result => { settled = true; return result; });
  await ready;
  assert.equal(settled, false);
  assert.equal(calls[1][1].body, file);
  assert.equal(calls[1][1].headers['If-None-Match'], '*');
  assert.equal(calls[2][0], '/api/v1/files/avatars/m1/complete');
  complete(envelope({ id: 'm1', status: 'TEMP', url: '/api/v1/files/m1/content' }));
  assert.equal((await upload).url, '/api/v1/files/m1/content');
});

test('failed verification cannot produce a staged avatar result', async () => {
  const store = apiStore(async url => {
    if (url.endsWith('/presign')) return envelope(signed);
    if (url === signed.uploadUrl) return new Response(null, { status: 200 });
    return Response.json({ code: 4000, message: 'Invalid image' }, { status: 400 });
  });
  await assert.rejects(store.uploadAvatar(file), /Invalid image/);
});

test('generic image upload uses the shared pipeline and returned server URL', async () => {
  const routes = [];
  const store = apiStore(async (url, init) => {
    routes.push(url);
    if (url.endsWith('/presign')) {
      assert.equal(JSON.parse(init.body).visibility, 'PRIVATE');
      return envelope(signed);
    }
    if (url === signed.uploadUrl) return new Response(null, { status: 200 });
    return envelope({ id: 'm1', status: 'ACTIVE', url: '/media/server-url' });
  });
  assert.equal(await store.uploadImage(file, 'returns'), '/media/server-url');
  assert.equal(routes[0], '/api/v1/files/presign');
  assert.equal(routes[2], '/api/v1/files/m1/complete');
});
