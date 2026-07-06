import { create } from 'zustand';
import type { ChatMessage, MessagePart, PaymentSession, TraceEntry } from './types';
import { streamChat } from './api';

/** Generates a stable client-side id. */
function uid(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : Math.random().toString(36).slice(2);
}

/** Controls the in-flight chat request so a new send or reset can cancel it. */
let activeController: AbortController | null = null;

/** True when the error was caused by aborting the request (not a real failure). */
function isAbort(e: unknown): boolean {
  return e instanceof DOMException ? e.name === 'AbortError' : (e as Error)?.name === 'AbortError';
}

interface ChatStore {
  messages: ChatMessage[];
  sessionId: string | null;
  isStreaming: boolean;
  traces: TraceEntry[];

  sendMessage: (text: string) => Promise<void>;
  applyPaymentSession: (messageId: string, session: PaymentSession) => void;
  reset: () => void;
}

export const useChatStore = create<ChatStore>((set, get) => ({
  messages: [],
  sessionId: null,
  isStreaming: false,
  traces: [],

  async sendMessage(text: string) {
    if (get().isStreaming || !text.trim()) return;

    // Cancel any lingering request before starting a new one.
    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;

    const userMsg: ChatMessage = {
      id: uid(),
      role: 'user',
      parts: [{ kind: 'text', text }],
    };
    const assistantId = uid();
    const assistantMsg: ChatMessage = { id: assistantId, role: 'assistant', parts: [] };

    set((s) => ({
      messages: [...s.messages, userMsg, assistantMsg],
      isStreaming: true,
    }));

    /** Mutates the assistant message's parts immutably. */
    const patch = (fn: (parts: MessagePart[]) => MessagePart[]) =>
      set((s) => ({
        messages: s.messages.map((m) =>
          m.id === assistantId ? { ...m, parts: fn(m.parts) } : m,
        ),
      }));

    try {
      for await (const event of streamChat(
        text,
        get().sessionId,
        (id) => set({ sessionId: id }),
        controller.signal,
      )) {
        switch (event.type) {
          case 'token':
            patch((parts) => {
              const last = parts[parts.length - 1];
              if (last && last.kind === 'text') {
                return [...parts.slice(0, -1), { kind: 'text', text: last.text + event.text }];
              }
              return [...parts, { kind: 'text', text: event.text }];
            });
            break;
          case 'product_cards':
            patch((parts) => [...parts, { kind: 'products', products: event.products }]);
            break;
          case 'payment_form':
            patch((parts) => [...parts, { kind: 'payment', form: event }]);
            break;
          case 'payment_result':
            patch((parts) => [
              ...parts,
              { kind: 'payment_result', success: event.success, message: event.message },
            ]);
            break;
          case 'trace':
            set((s) => ({
              traces: [
                ...s.traces,
                { id: uid(), category: event.category, message: event.message, ts: event.ts },
              ].slice(-200),
            }));
            break;
          case 'error':
            patch((parts) => [...parts, { kind: 'text', text: `⚠️ ${event.message}` }]);
            break;
          case 'done':
            break;
        }
      }
    } catch (e) {
      // An aborted request (new send or reset) is not an error to show.
      if (!isAbort(e)) {
        patch((parts) => [
          ...parts,
          { kind: 'text', text: `⚠️ Connection error: ${(e as Error).message}` },
        ]);
      }
    } finally {
      if (activeController === controller) activeController = null;
      set({ isStreaming: false });
    }
  },

  applyPaymentSession(messageId, session) {
    const terminal = session.state === 'SUCCESS' || session.state === 'FAILED';
    set((s) => ({
      messages: s.messages.map((m) => {
        if (m.id !== messageId) return m;
        const parts = m.parts.map((p): MessagePart => {
          if (p.kind !== 'payment' || p.form.sessionId !== session.sessionId) return p;
          if (terminal) {
            return {
              kind: 'payment_result',
              success: session.state === 'SUCCESS',
              message:
                session.state === 'SUCCESS'
                  ? 'Payment successful — thank you!'
                  : session.failureReason ?? 'Payment failed.',
            };
          }
          return {
            kind: 'payment',
            form: { ...p.form, state: session.state, maskedCard: session.maskedCard },
          };
        });
        return { ...m, parts };
      }),
    }));
  },

  reset() {
    activeController?.abort();
    activeController = null;
    set({ messages: [], sessionId: null, isStreaming: false, traces: [] });
  },
}));
