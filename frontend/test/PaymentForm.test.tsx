import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PaymentForm } from '@/components/PaymentForm';
import type { AgentEvent, PaymentState } from '@/lib/types';

function form(state: PaymentState): Extract<AgentEvent, { type: 'payment_form' }> {
  return {
    type: 'payment_form',
    sessionId: 's1',
    state,
    orderId: 'elec-001',
    amountRial: 42_000_000,
    amountToman: 4_200_000,
    maskedCard: null,
  };
}

describe('PaymentForm', () => {
  it('shows the card step first', () => {
    render(<PaymentForm form={form('AWAITING_CARD')} messageId="m1" />);
    expect(screen.getByText('Card number')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
  });

  it('shows the OTP step with the demo hint and a pay button', () => {
    render(<PaymentForm form={form('AWAITING_OTP')} messageId="m1" />);
    expect(screen.getByText(/one-time password/i)).toBeInTheDocument();
    expect(screen.getByText(/demo otp is 12345/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /pay now/i })).toBeInTheDocument();
  });
});
