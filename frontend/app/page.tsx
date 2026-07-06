import { ChatWindow } from '@/components/ChatWindow';
import { LogPanel } from '@/components/LogPanel';

export default function Home() {
  return (
    <div className="flex h-[100dvh] w-full">
      <div className="min-w-0 flex-1">
        <ChatWindow />
      </div>
      <LogPanel />
    </div>
  );
}
