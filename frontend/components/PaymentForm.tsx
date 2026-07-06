'use client';

import { useState } from 'react';
import type { AgentEvent } from '@/lib/types';
import { submitCardDetails, submitOtp } from '@/lib/api';
import { useChatStore } from '@/lib/store';

type Form = Extract<AgentEvent, { type: 'payment_form' }>;

interface Props {
  form: Form;
  messageId: string;
}

const STEP_LABELS = ['Card details', 'OTP'];

export function PaymentForm({ form, messageId }: Props) {
  const applyPaymentSession = useChatStore((s) => s.applyPaymentSession);
  const [card, setCard] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvv2, setCvv2] = useState('');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onCardDetails = form.state === 'AWAITING_CARD_DETAILS';
  const onOtp = form.state === 'AWAITING_OTP';
  const stepIndex = onOtp ? 1 : 0;

  async function run(fn: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const submitCard = () =>
    run(async () => {
      const session = await submitCardDetails(form.sessionId, {
        cardNumber: card.trim(),
        expiry: expiry.trim(),
        cvv2: cvv2.trim(),
      });
      applyPaymentSession(messageId, session);
    });

  const submitCode = () =>
    run(async () => {
      const session = await submitOtp(form.sessionId, otp.trim());
      applyPaymentSession(messageId, session);
    });

  if (!onCardDetails && !onOtp) return null;

  const cardReady = card.trim().length >= 16 && expiry.trim().length >= 4 && cvv2.trim().length >= 3;

  return (
    <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-sm font-semibold">Secure payment</h4>
        <span className="text-xs font-medium text-slate-500">
          {form.amountToman.toLocaleString('en-US')} Toman
        </span>
      </div>

      {/* Two-step progress: card details, then OTP */}
      <ol className="mb-3 flex gap-1.5">
        {STEP_LABELS.map((label, i) => (
          <li
            key={label}
            className={`h-1.5 flex-1 rounded-full ${
              i < stepIndex ? 'bg-brand' : i === stepIndex ? 'bg-brand/60' : 'bg-slate-200'
            }`}
          />
        ))}
      </ol>

      {onCardDetails && (
        <div className="space-y-2">
          <div>
            <label className="block text-xs font-medium text-slate-600">Card number</label>
            <input
              autoFocus
              value={card}
              inputMode="numeric"
              maxLength={19}
              placeholder="6037 9911 2233 4455"
              onChange={(e) => setCard(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />
          </div>
          <div className="flex gap-2">
            <div className="flex-1">
              <label className="block text-xs font-medium text-slate-600">Expiry (MM/YY)</label>
              <input
                value={expiry}
                inputMode="numeric"
                maxLength={5}
                placeholder="MM/YY"
                onChange={(e) => setExpiry(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
              />
            </div>
            <div className="w-24">
              <label className="block text-xs font-medium text-slate-600">CVV2</label>
              <input
                value={cvv2}
                inputMode="numeric"
                maxLength={4}
                placeholder="***"
                onChange={(e) => setCvv2(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && cardReady && submitCard()}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
              />
            </div>
          </div>
          {error && <p className="text-xs text-red-600">{error}</p>}
          <button
            type="button"
            onClick={submitCard}
            disabled={busy || !cardReady}
            className="mt-1 w-full rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white transition hover:bg-brand-dark disabled:bg-slate-300"
          >
            {busy ? 'Sending…' : 'Continue'}
          </button>
        </div>
      )}

      {onOtp && (
        <div className="space-y-2">
          {form.maskedCard && <p className="font-mono text-xs text-slate-500">{form.maskedCard}</p>}
          <label className="block text-xs font-medium text-slate-600">
            One-time password (OTP)
          </label>
          <input
            autoFocus
            value={otp}
            inputMode="numeric"
            maxLength={8}
            placeholder="e.g. 12345"
            onChange={(e) => setOtp(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && otp.trim() && submitCode()}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
          <p className="text-[11px] text-slate-400">A code was sent to the cardholder. Demo OTP is 12345.</p>
          {error && <p className="text-xs text-red-600">{error}</p>}
          <button
            type="button"
            onClick={submitCode}
            disabled={busy || !otp.trim()}
            className="w-full rounded-lg bg-brand px-3 py-2 text-sm font-semibold text-white transition hover:bg-brand-dark disabled:bg-slate-300"
          >
            {busy ? 'Processing…' : 'Pay now'}
          </button>
        </div>
      )}
    </div>
  );
}
