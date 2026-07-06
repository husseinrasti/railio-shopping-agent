import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ProductCard } from '@/components/ProductCard';
import type { Product } from '@/lib/types';

const product: Product = {
  id: 'elec-001',
  name: 'Aurora Wireless Headphones',
  description: 'Noise-cancelling over-ear headphones.',
  category: 'electronics',
  categoryLabel: 'Electronics',
  priceRial: 42_000_000,
  priceToman: 4_200_000,
  currency: 'IRR',
  imageUrl: 'https://picsum.photos/seed/elec-001/400/300',
  stock: 10,
  inStock: true,
  rating: 4.6,
  attributes: {},
};

describe('ProductCard', () => {
  it('renders name, formatted Toman price and a buy button', () => {
    render(<ProductCard product={product} onBuy={() => {}} />);
    expect(screen.getByText('Aurora Wireless Headphones')).toBeInTheDocument();
    expect(screen.getByText('4,200,000 Toman')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /buy now/i })).toBeEnabled();
  });

  it('calls onBuy when clicked', () => {
    const onBuy = vi.fn();
    render(<ProductCard product={product} onBuy={onBuy} />);
    fireEvent.click(screen.getByRole('button', { name: /buy now/i }));
    expect(onBuy).toHaveBeenCalledWith(product);
  });

  it('disables purchase when out of stock', () => {
    render(<ProductCard product={{ ...product, inStock: false, stock: 0 }} onBuy={() => {}} />);
    expect(screen.getByRole('button', { name: /out of stock/i })).toBeDisabled();
  });
});
