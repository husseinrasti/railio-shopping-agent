'use client';

import { useEffect, useRef } from 'react';
import type { TraceCategory } from '@/lib/types';
import { useChatStore } from '@/lib/store';

/** Per-category glyph + colour for a trace line. */
const CATEGORY_STYLE: Record<TraceCategory, { icon: string; className: string }> = {
  flow: { icon: '▶', className: 'text-sky-300' },
  model: { icon: '🧠', className: 'text-violet-300' },
  tool: { icon: '🔧', className: 'text-emerald-300' },
  payment: { icon: '💳', className: 'text-amber-300' },
  system: { icon: '⚙', className: 'text-slate-400' },
};

/** Formats an epoch-millis timestamp as HH:MM:SS. */
function clock(ts: number): string {
  return new Date(ts).toLocaleTimeString('en-GB', { hour12: false });
}

/**
 * Developer log panel: a live console of everything the agent does behind the
 * scenes (flow, tool calls, model output, payment steps, model release).
 *
 * Desktop-only — hidden below the `lg` breakpoint so the mobile UI stays a clean
 * chat surface.
 */
export function LogPanel() {
  const traces = useChatStore((s) => s.traces);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [traces]);

  return (
    <aside className="hidden w-96 shrink-0 flex-col border-l border-slate-800 bg-slate-900 text-slate-100 lg:flex">
      <header className="flex items-center justify-between border-b border-slate-800 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-400" />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
            Agent activity
          </h2>
        </div>
        <span className="text-[11px] text-slate-500">{traces.length} events</span>
      </header>

      <div className="scroll-thin flex-1 overflow-y-auto p-3 font-mono text-[11px] leading-relaxed">
        {traces.length === 0 ? (
          <p className="mt-8 text-center text-slate-600">
            Behind-the-scenes agent steps appear here.
          </p>
        ) : (
          traces.map((t) => {
            const style = CATEGORY_STYLE[t.category] ?? CATEGORY_STYLE.system;
            return (
              <div key={t.id} className="mb-1.5 flex gap-2">
                <span className="shrink-0 text-slate-600">{clock(t.ts)}</span>
                <span className="shrink-0">{style.icon}</span>
                <span className={`whitespace-pre-wrap break-words ${style.className}`}>
                  {t.message}
                </span>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>
    </aside>
  );
}
