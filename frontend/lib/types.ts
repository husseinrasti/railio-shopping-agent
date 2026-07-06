// Wire types mirroring the backend DTOs (ai.railio.shop.web.dto).

export interface Product {
  id: string;
  name: string;
  description: string;
  category: string;
  categoryLabel: string;
  priceRial: number;
  priceToman: number;
  currency: string;
  imageUrl: string;
  stock: number;
  inStock: boolean;
  rating: number;
  attributes: Record<string, string>;
}

/** Payment lifecycle states, mirroring the backend PaymentState enum. */
export type PaymentState =
  | 'AWAITING_CARD_DETAILS'
  | 'AWAITING_OTP'
  | 'SUCCESS'
  | 'FAILED';

/** Streamed agent events (SSE `data:` payloads), discriminated by `type`. */
export type AgentEvent =
  | { type: 'token'; text: string }
  | { type: 'product_cards'; products: Product[] }
  | {
      type: 'payment_form';
      sessionId: string;
      state: PaymentState;
      orderId: string;
      amountRial: number;
      amountToman: number;
      maskedCard?: string | null;
    }
  | { type: 'payment_result'; sessionId: string; success: boolean; message: string }
  | { type: 'error'; message: string }
  | { type: 'done' };

/** A payment session snapshot returned by REST payment endpoints. */
export interface PaymentSession {
  sessionId: string;
  orderId: string;
  amountRial: number;
  amountToman: number;
  state: PaymentState;
  maskedCard?: string | null;
  failureReason?: string | null;
}

/** A rendered chat message; `parts` interleave text and structured widgets. */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  parts: MessagePart[];
}

export type MessagePart =
  | { kind: 'text'; text: string }
  | { kind: 'products'; products: Product[] }
  | { kind: 'payment'; form: Extract<AgentEvent, { type: 'payment_form' }> }
  | { kind: 'payment_result'; success: boolean; message: string };
