import { createElement, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router';
import Root from './Root';
import RequireAuth from '../components/RequireAuth';
import { HomePage, ProductsPage, ProductDetailPage, CartPage, CheckoutPage, PaymentReturnPage } from '../pages/CommercePages';
import { ProfilePage, OrdersPage, OrderDetailPage, WishlistPage, ReturnPage } from '../pages/AccountPages';
import AddressesPage from '../pages/AddressesPage';
import Login from '../pages/Login';
import Register from '../pages/Register';
import Contact from '../pages/Contact';
import Policy from '../pages/Policy';
import NotFound from '../pages/NotFound';

const protectedPage = (element: ReactNode) => createElement(RequireAuth, null, element);

export const router = createBrowserRouter([{
  path: '/', Component: Root, children: [
    { index: true, Component: HomePage },
    { path: 'products', Component: ProductsPage },
    { path: 'products/:id', Component: ProductDetailPage },
    { path: 'cart', element: protectedPage(createElement(CartPage)) },
    { path: 'checkout', element: protectedPage(createElement(CheckoutPage)) },
    { path: 'login', Component: Login },
    { path: 'register', Component: Register },
    { path: 'profile', element: protectedPage(createElement(ProfilePage)) },
    { path: 'profile/addresses', element: protectedPage(createElement(AddressesPage)) },
    { path: 'profile/orders', element: protectedPage(createElement(OrdersPage)) },
    { path: 'profile/orders/:id', element: protectedPage(createElement(OrderDetailPage)) },
    { path: 'profile/orders/:id/return', element: protectedPage(createElement(ReturnPage)) },
    { path: 'wishlist', element: protectedPage(createElement(WishlistPage)) },
    { path: 'payment/vnpay/return', Component: PaymentReturnPage },
    { path: 'payment/payos/success', Component: PaymentReturnPage },
    { path: 'payment/payos/cancel', Component: PaymentReturnPage },
    { path: 'contact', Component: Contact },
    { path: 'policy', Component: Policy },
    { path: '*', Component: NotFound },
  ],
}]);
