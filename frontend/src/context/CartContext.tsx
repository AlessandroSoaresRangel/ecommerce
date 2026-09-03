import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import * as cartApi from "../services/cart";
import type { CartResponse } from "../types/api";
import { useAuth } from "../hooks/useAuth";

export interface CartContextValue {
  cart: CartResponse | null;
  itemCount: number;
  loading: boolean;
  refresh: () => Promise<void>;
  addItem: (productId: number, quantity: number) => Promise<void>;
  updateItemQuantity: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
}

export const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<CartResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!user) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      setCart(await cartApi.getCart());
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const addItem = useCallback(async (productId: number, quantity: number) => {
    setCart(await cartApi.addCartItem({ productId, quantity }));
  }, []);

  const updateItemQuantity = useCallback(async (itemId: number, quantity: number) => {
    setCart(await cartApi.updateCartItemQuantity(itemId, quantity));
  }, []);

  const removeItem = useCallback(async (itemId: number) => {
    setCart(await cartApi.removeCartItem(itemId));
  }, []);

  const itemCount = useMemo(() => cart?.items.reduce((n, i) => n + i.quantity, 0) ?? 0, [cart]);

  const value = useMemo(
    () => ({ cart, itemCount, loading, refresh, addItem, updateItemQuantity, removeItem }),
    [cart, itemCount, loading, refresh, addItem, updateItemQuantity, removeItem],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
