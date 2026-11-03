import { readChatHistory, renameChat, deleteChat } from "@/chatHistory";
import type { Message } from "@/components/chat/ChatMessage";
import { useEffect, useState } from 'react';
import { Clock, MessageSquare, Trash2, Edit2, Check, X, RefreshCw, Plus, Database } from 'lucide-react';
import {
  listConversations,
  getConversationMessages,
  updateConversationTitle,
  deleteConversation,
  saveConversationSnapshot,
  getDatabaseStatus,
  type ConversationRecord,
  type DatabaseStatus,
} from '@/api';

type Props = {
  token: string;
  busy: boolean;
  activeConversationId?: string;
  currentMessages: Message[];
  onSelectConversation: (id: string, title: string, messages: Message[]) => void;
  onNewConversation: () => void;
  onClose: () => void;
};

export default function ConversationsDrawer({
  token,
  busy,
  activeConversationId,
  currentMessages,
  onSelectConversation,
  onNewConversation,
  onClose,
}: Props) {
  const [conversations, setConversations] = useState<ConversationRecord[]>([]);
  const [status, setStatus] = useState<DatabaseStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const local = readChatHistory();
      setConversations(local);
      const dbStatus = await getDatabaseStatus(token);
      setStatus(dbStatus);
      if (dbStatus.ready) {
        const list: ConversationRecord[] = [];
        for (let offset = 0; ; offset += 100) {
          const page = await listConversations(token, 100, offset);
          list.push(...page);
          if (page.length < 100) break;
        }
        setConversations([...local, ...list.filter(c => !local.some(l => l.id === c.id))]
          .sort((a, b) => b.updated_at.localeCompare(a.updated_at)));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load database conversations.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!busy) void loadData();
  }, [token, busy]);

  async function handleSelect(c: ConversationRecord) {
    if (busy) return;
    setLoading(true);
    setError('');
    try {
      const local = readChatHistory().find(chat => chat.id === c.id);
      if (local) {
        onSelectConversation(c.id, c.title, local.messages);
        onClose();
        return;
      }
      const msgs = [];
      for (let after = 0; ; ) {
        const page = await getConversationMessages(token, c.id, after, 100);
        msgs.push(...page);
        if (page.length < 100) break;
        after = page[page.length - 1].id;
      }
      onSelectConversation(
        c.id,
        c.title,
        msgs.map(m => ({ id: crypto.randomUUID(), role: m.role, content: m.content })),
      );
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load messages.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveCurrent() {
    if (currentMessages.length === 0) return;
    setSaving(true);
    setError('');
    try {
      const firstUserMsg = currentMessages.find(m => m.role === 'user')?.content || 'Conversation';
      const defaultTitle = firstUserMsg.slice(0, 50).trim() || 'Saved Chat';
      const saved = await saveConversationSnapshot(token, defaultTitle, currentMessages);
      setConversations(prev => [saved, ...prev]);

    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save snapshot.');
    } finally {
      setSaving(false);
    }
  }

  async function handleRename(c: ConversationRecord) {
    if (!editTitle.trim()) return;
    try {
      const updated = readChatHistory().some(chat => chat.id === c.id)
        ? renameChat(c.id, editTitle.trim())
        : await updateConversationTitle(token, c.id, c.version, editTitle.trim());
      setConversations(prev => prev.map(item => (item.id === c.id ? updated : item)));
      setEditingId(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not rename conversation.');
    }
  }

  async function handleDelete(c: ConversationRecord) {
    if (busy) return;
    if (!window.confirm(`Delete conversation "${c.title}"?`)) return;
    try {
      if (!c.id.startsWith("local-")) await deleteConversation(token, c.id, c.version);
      deleteChat(c.id);
      setConversations(prev => prev.filter(item => item.id !== c.id));
      if (activeConversationId === c.id) {
        onNewConversation();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete conversation.');
    }
  }

  return (
    <aside className="conversations-drawer" aria-label="Saved conversations">
      <div className="drawer-header">
        <div className="drawer-title">
          <Database size={17} />
          <h2>Saved Conversations</h2>
        </div>
        <div className="drawer-actions">
          <button className="icon-button" title="Refresh" onClick={loadData} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
          </button>
          <button className="icon-button" title="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </div>
      </div>

      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="drawer-top-actions">
        <button
          className="primary-button"
          disabled={busy || saving || currentMessages.length === 0 || !status?.ready}
          onClick={handleSaveCurrent}
        >
          <Plus size={15} /> {saving ? 'Saving snapshot…' : 'Save current chat to database'}
        </button>
      </div>

      <p>Chats are saved automatically in this browser.</p>
      {conversations.length === 0 ? (
        <div className="drawer-empty-state">
          <MessageSquare size={32} />
          <p>No saved conversations yet.</p>
          <small>Start a conversation and it will appear here automatically.</small>
        </div>
      ) : (
        <div className="conversation-list">
          {conversations.map(c => {
            const isEditing = editingId === c.id;
            const isActive = activeConversationId === c.id;
            return (
              <div key={c.id} className={`conversation-item ${isActive ? 'active' : ''}`}>
                {isEditing ? (
                  <div className="conversation-edit-row">
                    <input
                      type="text"
                      value={editTitle}
                      maxLength={100}
                      autoFocus
                      onChange={e => setEditTitle(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter') void handleRename(c);
                        if (e.key === 'Escape') setEditingId(null);
                      }}
                    />
                    <button className="icon-button" onClick={() => void handleRename(c)} title="Save title">
                      <Check size={14} />
                    </button>
                    <button className="icon-button" onClick={() => setEditingId(null)} title="Cancel">
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <>
                    <button className="conversation-select-button" disabled={busy} onClick={() => void handleSelect(c)}>
                      <MessageSquare size={14} />
                      <div className="conversation-info">
                        <span className="conversation-title">{c.title}</span>
                        <span className="conversation-date">
                          <Clock size={10} />
                          {new Date(c.created_at).toLocaleDateString(undefined, {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>
                    </button>
                    <div className="conversation-item-actions">
                      <button
                        className="icon-button"
                        title="Rename"
                        onClick={() => {
                          setEditingId(c.id);
                          setEditTitle(c.title);
                        }}
                      >
                        <Edit2 size={13} />
                      </button>
                      <button className="icon-button" title="Delete" disabled={busy} onClick={() => void handleDelete(c)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </>
                )}
              </div>
            );
          })}
        </div>
      )}
    </aside>
  );
}
