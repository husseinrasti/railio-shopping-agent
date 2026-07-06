'use client';

import type { ChatMessage, Product } from '@/lib/types';
import { useChatStore } from '@/lib/store';
import { ProductCard } from './ProductCard';
import { PaymentForm } from './PaymentForm';

interface Props {
  message: ChatMessage;
}

/** Renders a chat message, interleaving text, product cards and payment forms. */
export function MessageBubble({ message }: Props) {
  const sendMessage = useChatStore((s) => s.sendMessage);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const isUser = message.role === 'user';

  const buy = (product: Product) =>
    sendMessage(`I'd like to buy ${product.name} (${product.id}).`);

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`flex max-w-[85%] flex-col gap-2 ${isUser ? 'items-end' : 'items-start'}`}>
        {message.parts.map((part, i) => {
          switch (part.kind) {
            case 'text':
              return (
                <div
                  key={i}
                  className={`whitespace-pre-wrap rounded-2xl px-4 py-2 text-sm ${
                    isUser
                      ? 'bg-brand text-white'
                      : 'bg-white text-slate-800 shadow-sm ring-1 ring-slate-100'
                  }`}
                >
                  {part.text}
                </div>
              );
            case 'products':
              return (
                <div key={i} className="scroll-thin flex gap-3 overflow-x-auto pb-1">
                  {part.products.map((p) => (
                    <ProductCard key={p.id} product={p} onBuy={buy} disabled={isStreaming} />
                  ))}
                </div>
              );
            case 'payment':
              return <PaymentForm key={i} form={part.form} messageId={message.id} />;
            case 'payment_result':
              return (
                <div
                  key={i}
                  className={`rounded-xl px-4 py-2 text-sm font-medium ${
                    part.success
                      ? 'bg-green-50 text-green-700 ring-1 ring-green-200'
                      : 'bg-red-50 text-red-700 ring-1 ring-red-200'
                  }`}
                >
                  {part.success ? '✅ ' : '❌ '}
                  {part.message}
                </div>
              );
            default:
              return null;
          }
        })}
      </div>
    </div>
  );
}
