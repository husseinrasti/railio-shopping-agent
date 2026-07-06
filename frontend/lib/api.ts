import type { AgentEvent, PaymentSession } from './types';

/** Backend base URL, configurable at build/run time. */
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '') || 'http://localhost:8080';

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

/** Submits card number, expiry and CVV2 together; provider then issues the OTP. */
export async function submitCardDetails(
  sessionId: string,
  details: { cardNumber: string; expiry: string; cvv2: string },
): Promise<PaymentSession> {
  return postPayment(`${sessionId}/card-details`, details);
}

/** Submits the OTP to complete (or fail) the payment. */
export async function submitOtp(sessionId: string, otp: string): Promise<PaymentSession> {
  return postPayment(`${sessionId}/otp`, { value: otp });
}

async function postPayment(path: string, body: unknown): Promise<PaymentSession> {
  const res = await fetch(`${API_URL}/api/payment/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data?.error ?? 'Payment request failed');
  return data as PaymentSession;
}
