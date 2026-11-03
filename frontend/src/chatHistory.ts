import type { ConversationRecord } from './api';
import type { Message } from './components/chat/ChatMessage';

export type SavedChat = ConversationRecord & { messages: Message[] };
const KEY = 'jarvis_chat_history_v1';

export function readChatHistory(): SavedChat[] {
  const raw = localStorage.getItem(KEY);
  if (!raw) return [];
  const chats = JSON.parse(raw) as SavedChat[];
  if (!Array.isArray(chats)) throw new Error('Cannot read saved chat history.');
  return chats;
}

export function saveChat(id: string, messages: Message[]): void {
  if (!messages.length) return;
  const chats = readChatHistory();
  const previous = chats.find(chat => chat.id === id);
  const now = new Date().toISOString();
  const chat: SavedChat = {
    id, title: previous?.title ?? (messages.find(m => m.role === 'user')?.content.slice(0, 80) || 'New chat'),
    version: (previous?.version ?? 0) + 1,
    created_at: previous?.created_at ?? now, updated_at: now, messages,
  };
  localStorage.setItem(KEY, JSON.stringify([chat, ...chats.filter(c => c.id !== id)]));
}

export function renameChat(id: string, title: string): SavedChat {
  const chats = readChatHistory();
  const chat = chats.find(c => c.id === id);
  if (!chat) throw new Error('Chat no longer exists.');
  chat.title = title;
  localStorage.setItem(KEY, JSON.stringify(chats));
  return chat;
}

export function deleteChat(id: string): void {
  localStorage.setItem(KEY, JSON.stringify(readChatHistory().filter(c => c.id !== id)));
}
