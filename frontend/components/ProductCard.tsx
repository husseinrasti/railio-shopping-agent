'use client';

import type { Product } from '@/lib/types';

/** Formats a Toman amount with thousands separators. */
function formatToman(toman: number): string {
  return `${toman.toLocaleString('en-US')} Toman`;
}

interface Props {
  product: Product;
  onBuy: (product: Product) => void;
  disabled?: boolean;
}

/** A single product card rendered inline in the chat. */
export function ProductCard({ product, onBuy, disabled }: Props) {
  return (
    <div className="flex w-56 shrink-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={product.imageUrl}
        alt={product.name}
        className="h-32 w-full object-cover"
        loading="lazy"
      />
      <div className="flex flex-1 flex-col gap-1 p-3">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-sm font-semibold leading-tight">{product.name}</h3>
          <span className="shrink-0 rounded bg-brand-light px-1.5 py-0.5 text-[10px] font-medium text-brand-dark">
            ★ {product.rating.toFixed(1)}
          </span>
        </div>
        <p className="line-clamp-2 text-xs text-slate-500">{product.description}</p>
        <div className="mt-auto pt-2">
          <p className="text-sm font-bold text-slate-900">{formatToman(product.priceToman)}</p>
          <button
            type="button"
            disabled={disabled || !product.inStock}
            onClick={() => onBuy(product)}
            className="mt-2 w-full rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {product.inStock ? 'Buy now' : 'Out of stock'}
          </button>
        </div>
      </div>
    </div>
  );
}
