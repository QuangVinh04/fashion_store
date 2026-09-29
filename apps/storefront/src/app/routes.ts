import { createElement, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router';
import Root from './Root';
import RequireAuth from '../components/RequireAuth';
import Home from '../pages/Home';
import Products from '../pages/Products';
import ProductDetail from '../pages/ProductDetail';
import Cart from '../pages/Cart';
import Checkout from '../pages/Checkout';
import PaymentReturn from '../pages/PaymentReturn';
import Profile from '../pages/Profile';
import AddressesPage from '../pages/AddressesPage';
import Orders from '../pages/Orders';
import OrderDetail from '../pages/OrderDetail';
import ReturnPage from '../pages/ReturnPage';
import Wishlist from '../pages/Wishlist';
import Login from '../pages/Login';
import Register from '../pages/Register';
import Contact from '../pages/Contact';
import Policy from '../pages/Policy';
import NotFound from '../pages/NotFound';

const protectedPage = (element: ReactNode) => createElement(RequireAuth, null, element);

export const router = createBrowserRouter([
  {
    path: '/',
    Component: Root,
    children: [
      { index: true, Component: Home },
      { path: 'products', Component: Products },
      { path: 'products/:id', Component: ProductDetail },
      { path: 'cart', element: protectedPage(createElement(Cart)) },
      { path: 'checkout', element: protectedPage(createElement(Checkout)) },
      { path: 'login', Component: Login },
      { path: 'register', Component: Register },
      { path: 'profile', element: protectedPage(createElement(Profile)) },
      { path: 'profile/addresses', element: protectedPage(createElement(AddressesPage)) },
      { path: 'profile/orders', element: protectedPage(createElement(Orders)) },
      { path: 'profile/orders/:id', element: protectedPage(createElement(OrderDetail)) },
      { path: 'profile/orders/:id/return', element: protectedPage(createElement(ReturnPage)) },
      { path: 'wishlist', element: protectedPage(createElement(Wishlist)) },
      { path: 'payment/vnpay/return', Component: PaymentReturn },
      { path: 'payment/payos/success', Component: PaymentReturn },
      { path: 'payment/payos/cancel', Component: PaymentReturn },
      { path: 'contact', Component: Contact },
      { path: 'policy', Component: Policy },
      { path: '*', Component: NotFound },
    ],
  },
]);
