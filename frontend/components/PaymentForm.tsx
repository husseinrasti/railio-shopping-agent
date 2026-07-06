'use client';

import { useState } from 'react';
import type { AgentEvent, PaymentState } from '@/lib/types';
import { stepForState, submitPaymentStep } from '@/lib/api';
import { useChatStore } from '@/lib/store';

type Form = Extract<AgentEvent, { type: 'payment_form' }>;

/** Per-step UI configuration for the Iranian card flow. */
const STEP_META: Record<
  string,
  { label: string; placeholder: string; maxLength: number; inputMode: 'numeric' | 'text' }
> = {
  AWAITING_CARD: { label: 'Card number', placeholder: '6037 9911 2233 4455', maxLength: 19, inputMode: 'numeric' },
  AWAITING_EXPIRY: { label: 'Expiry (MM/YY)', placeholder: 'MM/YY', maxLength: 5, inputMode: 'numeric' },
  AWAITING_CVV2: { label: 'CVV2', placeholder: '***', maxLength: 4, inputMode: 'numeric' },
  AWAITING_OTP: { label: 'One-time password (OTP)', placeholder: 'e.g. 12345', maxLength: 8, inputMode: 'numeric' },
};

const STEP_ORDER: PaymentState[] = ['AWAITING_CARD', 'AWAITING_EXPIRY', 'AWAITING_CVV2', 'AWAITING_OTP'];

interface Props {
  form: Form;
  messageId: string;
}

export function PaymentForm({ form, messageId }: Props) {
  const applyPaymentSession = useChatStore((s) => s.applyPaymentSession);
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const meta = STEP_META[form.state];
  const step = stepForState[form.state];
  const currentIndex = STEP_ORDER.indexOf(form.state);

  async function submit() {
    if (!step || !value.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const session = await submitPaymentStep(form.sessionId, step, value.trim());
      setValue('');
      applyPaymentSession(messageId, session);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!meta || !step) return null;

  return (
    <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-sm font-semibold">Secure payment</h4>
        <span className="text-xs font-medium text-slate-500">
          {form.amountToman.toLocaleString('en-US')} Toman
        </span>
      </div>

      {/* Step progress */}
      <ol className="mb-3 flex gap-1.5">
        {STEP_ORDER.map((s, i) => (
          <li
            key={s}
            className={`h-1.5 flex-1 rounded-full ${
              i < currentIndex ? 'bg-brand' : i === currentIndex ? 'bg-brand/60' : 'bg-slate-200'
            }`}
          />
        ))}
      </ol>

      {form.maskedCard && (
        <p className="mb-2 font-mono text-xs text-slate-500">{form.maskedCard}</p>
      )}

      <label className="block text-xs font-medium text-slate-600">{meta.label}</label>
      <input
        autoFocus
        value={value}
        inputMode={meta.inputMode}
        maxLength={meta.maxLength}
        placeholder={meta.placeholder}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
      />

      {form.state === 'AWAITING_OTP' && (
        <p className="mt-1 text-[11px] text-slate-400">Demo OTP is 12345.</p>
      )}
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}

      <button
        type="button"
        onClick={submit}
        disabled={busy || !value.trim()}
        className="mt-3 w-full rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white transition hover:bg-brand-dark disabled:bg-slate-300"
      >
        {busy ? 'Processing…' : form.state === 'AWAITING_OTP' ? 'Pay now' : 'Continue'}
      </button>
    </div>
  );
}
