import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { transformWithEsbuild } from 'vite';

const source = await readFile(new URL('../src/api/payment-link.ts', import.meta.url), 'utf8');
const { code } = await transformWithEsbuild(source.replace(/^import .*;\r?\n/gm, ''), 'payment-link.ts', { format: 'cjs' });
const module = { exports: {} };
runInNewContext(code, { module, exports: module.exports, setTimeout });
const { requestPaymentUrl, paymentLinkMessage, PaymentLinkError } = module.exports;

const fail = status => Object.assign(new Error(`HTTP ${status}`), { status });
const noSleep = { sleep: async () => {} };
const pending = { id: 'pay-1', status: 'PENDING' };

test('waits while the payment is not created yet, then returns the link', async () => {
  let lookups = 0;
  const url = await requestPaymentUrl('o1', {
    payment: async () => { if (lookups++ < 2) throw fail(404); return pending; },
    paymentInitiate: async id => ({ paymentUrl: `https://pay/${id}` }),
  }, noSleep);
  assert.equal(url, 'https://pay/pay-1');
  assert.equal(lookups, 3);
});

test('gateway failure is reported immediately instead of being retried silently', async () => {
  let initiations = 0;
  await assert.rejects(requestPaymentUrl('o1', {
    payment: async () => pending,
    paymentInitiate: async () => { initiations++; throw fail(502); },
  }, noSleep), error => error instanceof PaymentLinkError && error.reason === 'gateway');
  assert.equal(initiations, 1);
});

test('cancelled or expired payment cannot open a link', async () => {
  await assert.rejects(requestPaymentUrl('o1', {
    payment: async () => ({ id: 'pay-1', status: 'CANCELLED' }),
    paymentInitiate: async () => assert.fail('must not initiate'),
  }, noSleep), error => error.reason === 'closed');
  await assert.rejects(requestPaymentUrl('o1', {
    payment: async () => pending,
    paymentInitiate: async () => { throw fail(400); },
  }, noSleep), error => error.reason === 'closed');
});

test('gives up with not-ready when the payment never appears', async () => {
  await assert.rejects(requestPaymentUrl('o1', {
    payment: async () => { throw fail(404); },
    paymentInitiate: async () => assert.fail('must not initiate'),
  }, { ...noSleep, attempts: 3 }), error => error.reason === 'not-ready');
});

test('unexpected errors keep their original cause', async () => {
  await assert.rejects(requestPaymentUrl('o1', {
    payment: async () => { throw fail(401); },
    paymentInitiate: async () => ({}),
  }, noSleep), error => error.status === 401);
  assert.equal(paymentLinkMessage(fail(401)), undefined);
  assert.match(paymentLinkMessage(new PaymentLinkError('gateway')), /hủy đơn hàng/);
});
