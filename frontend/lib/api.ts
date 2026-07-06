import type { AgentEvent, PaymentSession, PaymentState } from './types';

/** Backend base URL, configurable at build/run time. */
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '') || 'http://localhost:8080';

/** Maps a payment state to the REST step slug used to advance it. */
export const stepForState: Partial<Record<PaymentState, string>> = {
  AWAITING_CARD: 'card',
  AWAITING_EXPIRY: 'expiry',
  AWAITING_CVV2: 'cvv2',
  AWAITING_OTP: 'otp',
};

/**
 * Streams a chat turn. POSTs the message and yields each parsed {@link AgentEvent}
 * as it arrives over the SSE response. Returns the resolved session id via
 * {@link onSession} (from the `X-Session-Id` header).
 */
export async function* streamChat(
  message: string,
  sessionId: string | null,
  onSession: (id: string) => void,
  signal?: AbortSignal,
): AsyncGenerator<AgentEvent> {
  const res = await fetch(`${API_URL}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: sessionId ?? '', message }),
    signal,
  });

  const returnedSession = res.headers.get('X-Session-Id');
  if (returnedSession) onSession(returnedSession);

  if (!res.ok || !res.body) {
    throw new Error(`Chat request failed: ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // SSE frames are separated by a blank line.
    let sep: number;
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const dataLine = frame
        .split('\n')
        .find((l) => l.startsWith('data:'));
      if (!dataLine) continue;
      const json = dataLine.slice('data:'.length).trim();
      if (!json) continue;
      try {
        yield JSON.parse(json) as AgentEvent;
      } catch {
        // Ignore malformed frames.
      }
    }
  }
}

/** Submits one payment step and returns the updated session. */
export async function submitPaymentStep(
  sessionId: string,
  step: string,
  value: string,
): Promise<PaymentSession> {
  const res = await fetch(`${API_URL}/api/payment/${sessionId}/${step}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ value }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(body?.error ?? 'Payment step failed');
  return body as PaymentSession;
}
