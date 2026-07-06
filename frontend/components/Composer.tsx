'use client';

import { useState } from 'react';
import { useChatStore } from '@/lib/store';

const SUGGESTIONS = ['Show me headphones', 'What books do you have?', 'I need a gift under 2M Toman'];

/** Message input with quick suggestions. */
export function Composer() {
  const sendMessage = useChatStore((s) => s.sendMessage);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const hasMessages = useChatStore((s) => s.messages.length > 0);
  const [text, setText] = useState('');

  const send = (value: string) => {
    if (isStreaming || !value.trim()) return;
    sendMessage(value);
    setText('');
  };

  return (
    <div className="border-t border-slate-200 bg-white p-3">
      {!hasMessages && (
        <div className="mb-2 flex flex-wrap gap-2">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              onClick={() => send(s)}
              className="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600 transition hover:border-brand hover:text-brand"
            >
              {s}
            </button>
          ))}
        </div>
      )}
      <div className="flex items-end gap-2">
        <textarea
          value={text}
          rows={1}
          placeholder="Ask about products or start a purchase…"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              send(text);
            }
          }}
          className="max-h-32 flex-1 resize-none rounded-xl border border-slate-300 px-4 py-2.5 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
        />
        <button
          onClick={() => send(text)}
          disabled={isStreaming || !text.trim()}
          className="rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-dark disabled:bg-slate-300"
        >
          {isStreaming ? '…' : 'Send'}
        </button>
      </div>
    </div>
  );
}
