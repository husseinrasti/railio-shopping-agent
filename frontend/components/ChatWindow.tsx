'use client';

import { useEffect, useRef } from 'react';
import { useChatStore } from '@/lib/store';
import { MessageBubble } from './MessageBubble';
import { Composer } from './Composer';

/** The full chat surface: header, scrolling transcript, and composer. */
export function ChatWindow() {
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const reset = useChatStore((s) => s.reset);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  return (
    <div className="mx-auto flex h-full w-full max-w-2xl flex-col overflow-hidden bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand text-sm font-bold text-white">
            R
          </span>
          <div>
            <h1 className="text-sm font-semibold leading-tight">Railio Assistant</h1>
            <p className="text-[11px] text-slate-400">Shop &amp; pay by chat</p>
          </div>
        </div>
        {messages.length > 0 && (
          <button
            onClick={reset}
            className="text-xs text-slate-400 transition hover:text-slate-600"
          >
            New chat
          </button>
        )}
      </header>

      <main className="scroll-thin flex-1 space-y-4 overflow-y-auto overflow-x-hidden px-4 py-5">
        {messages.length === 0 ? (
          <EmptyState />
        ) : (
          messages.map((m) => <MessageBubble key={m.id} message={m} />)
        )}
        {isStreaming && messages[messages.length - 1]?.parts.length === 0 && (
          <div className="flex justify-start">
            <div className="flex gap-1 rounded-2xl bg-white px-4 py-3 shadow-sm ring-1 ring-slate-100">
              <Dot /> <Dot /> <Dot />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </main>

      <Composer />
    </div>
  );
}

function EmptyState() {
  return (
    <div className="mt-16 text-center animate-fade-in">
      <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-brand text-2xl text-white">
        🛍️
      </div>
      <h2 className="text-lg font-semibold">How can I help you shop?</h2>
      <p className="mx-auto mt-1 max-w-xs text-sm text-slate-500">
        Search the catalog, compare products, and check out securely — all in chat.
      </p>
    </div>
  );
}

function Dot() {
  return <span className="h-2 w-2 animate-bounce rounded-full bg-slate-300 [animation-delay:-0.2s]" />;
}
