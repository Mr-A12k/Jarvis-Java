import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { saveChat, readChatHistory, renameChat, deleteChat } from './chatHistory';

beforeEach(() => {
  const data = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => data.set(key, value),
  });
});
afterEach(() => vi.unstubAllGlobals());

it('retains complete long chats, images and interrupted replies across reads', () => {
  const messages = Array.from({ length: 125 }, (_, index) => ({
    id: String(index), role: 'user' as const, content: `Message ${index}`,
  }));
  const reply = { id: 'reply', role: 'assistant' as const, content: 'Partial answer',
    interrupted: true, imageUrl: 'data:image/png;base64,example', model: 'local-model' };
  saveChat('local-one', [...messages, reply]);
  expect(readChatHistory()[0].messages).toEqual([...messages, reply]);
});

it('updates an existing chat without duplicating it or losing its renamed title', () => {
  const message = { id: 'one', role: 'user' as const, content: 'Hello' };
  saveChat('local-one', [message]);
  renameChat('local-one', 'My chat');
  saveChat('local-two', [message]);
  saveChat('local-one', [message, { ...message, id: 'two' }]);
  expect(readChatHistory()).toHaveLength(2);
  expect(readChatHistory()[0].title).toBe('My chat');
  expect(readChatHistory()[0].messages).toHaveLength(2);
  deleteChat('local-one');
  expect(readChatHistory().map(c => c.id)).toEqual(['local-two']);
});

it('reports unavailable storage rather than silently losing history', () => {
  vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => { throw new Error('Full'); } });
  expect(() => saveChat('local-one', [{ id: 'one', role: 'user', content: 'Hello' }])).toThrow('Full');
});
