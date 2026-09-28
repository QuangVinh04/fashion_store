import { RouterProvider } from "react-router";
import { router } from "./routes";
import { CartProvider } from "../context/CartContext";
import { AuthProvider } from "../context/AuthContext";

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <RouterProvider router={router} />
      </CartProvider>
    </AuthProvider>
  );
}
