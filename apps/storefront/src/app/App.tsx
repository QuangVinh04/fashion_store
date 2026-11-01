import { RouterProvider } from "react-router";
import { MotionConfig } from "motion/react";
import { router } from "./routes";
import { CartProvider } from "../context/CartContext";
import { AuthProvider } from "../context/AuthContext";

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <AuthProvider>
        <CartProvider>
          <RouterProvider router={router} />
        </CartProvider>
      </AuthProvider>
    </MotionConfig>
  );
}
