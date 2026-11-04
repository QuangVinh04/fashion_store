import { createElement, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router';
import Root from './Root';
import RequireAuth from '../components/RequireAuth';
import ErrorPage from '../pages/ErrorPage';

const protectedPage = (element: ReactNode) => createElement(RequireAuth, null, element);

export const router = createBrowserRouter([
  {
    path: '/',
    Component: Root,
    errorElement: createElement(ErrorPage),
    children: [
      { index: true, lazy: () => import('../pages/Home').then(m => ({ Component: m.default })) },
      { path: 'products', lazy: () => import('../pages/Products').then(m => ({ Component: m.default })) },
      { path: 'products/:id', lazy: () => import('../pages/ProductDetail').then(m => ({ Component: m.default })) },
      { path: 'products/:id/reviews', lazy: () => import('../pages/ProductReviews').then(m => ({ Component: m.default })) },
      { path: 'cart', lazy: () => import('../pages/Cart').then(m => ({ Component: m.default })) },
      { path: 'checkout/wallet-pay', lazy: () => import('../pages/WalletPay').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/wallet', lazy: () => import('../pages/Wallet').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/wallet/top-up', lazy: () => import('../pages/WalletTopUp').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/wallet/top-ups/:id', lazy: () => import('../pages/WalletTopUp').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'checkout', lazy: () => import('../pages/Checkout').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'login', lazy: () => import('../pages/Login').then(m => ({ Component: m.default })) },
      { path: 'register', lazy: () => import('../pages/Register').then(m => ({ Component: m.default })) },
      { path: 'profile', lazy: () => import('../pages/Profile').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/addresses', lazy: () => import('../pages/AddressesPage').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/orders', lazy: () => import('../pages/Orders').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/orders/:id', lazy: () => import('../pages/OrderDetail').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'profile/orders/:id/return', lazy: () => import('../pages/ReturnPage').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'wishlist', lazy: () => import('../pages/Wishlist').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'payment/payos/success', lazy: () => import('../pages/PaymentReturn').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'payment/payos/cancel', lazy: () => import('../pages/PaymentReturn').then(m => ({ element: protectedPage(createElement(m.default)) })) },
      { path: 'contact', lazy: () => import('../pages/Contact').then(m => ({ Component: m.default })) },
      { path: 'policy', lazy: () => import('../pages/Policy').then(m => ({ Component: m.default })) },
      { path: '*', lazy: () => import('../pages/NotFound').then(m => ({ Component: m.default })) },
    ],
  },
]);
