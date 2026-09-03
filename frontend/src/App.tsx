import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { CartProvider } from "./context/CartContext";
import { FeedbackProvider } from "./context/FeedbackContext";
import { RequireAuth } from "./components/RequireAuth";
import { RequireAdmin } from "./components/RequireAdmin";
import { Layout } from "./components/Layout";

import { CatalogPage } from "./pages/CatalogPage";
import { ProductDetailPage } from "./pages/ProductDetailPage";
import { CartPage } from "./pages/CartPage";
import { CheckoutPage } from "./pages/CheckoutPage";
import { CheckoutSuccessPage } from "./pages/CheckoutSuccessPage";
import { CheckoutCancelPage } from "./pages/CheckoutCancelPage";
import { OrdersPage } from "./pages/OrdersPage";
import { OrderDetailPage } from "./pages/OrderDetailPage";
import { AdminProductsPage } from "./pages/AdminProductsPage";
import { AdminOrdersPage } from "./pages/AdminOrdersPage";
import { AuthPage } from "./pages/AuthPage";

export default function App() {
  return (
    <BrowserRouter>
      <FeedbackProvider>
        <AuthProvider>
          <CartProvider>
            <Layout>
              <Routes>
                <Route path="/" element={<CatalogPage />} />
                <Route path="/products/:id" element={<ProductDetailPage />} />
                <Route path="/auth" element={<AuthPage />} />

                <Route
                  path="/cart"
                  element={
                    <RequireAuth>
                      <CartPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="/checkout/success"
                  element={
                    <RequireAuth>
                      <CheckoutSuccessPage />
                    </RequireAuth>
                  }
                />
                <Route path="/checkout/cancel" element={<CheckoutCancelPage />} />
                <Route
                  path="/checkout/:orderId"
                  element={
                    <RequireAuth>
                      <CheckoutPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="/orders"
                  element={
                    <RequireAuth>
                      <OrdersPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="/orders/:id"
                  element={
                    <RequireAuth>
                      <OrderDetailPage />
                    </RequireAuth>
                  }
                />

                <Route
                  path="/admin/products"
                  element={
                    <RequireAdmin>
                      <AdminProductsPage />
                    </RequireAdmin>
                  }
                />
                <Route
                  path="/admin/orders"
                  element={
                    <RequireAdmin>
                      <AdminOrdersPage />
                    </RequireAdmin>
                  }
                />
              </Routes>
            </Layout>
          </CartProvider>
        </AuthProvider>
      </FeedbackProvider>
    </BrowserRouter>
  );
}
