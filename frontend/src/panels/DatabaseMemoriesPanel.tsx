import { useEffect, useState } from 'react';
import { Database, Plus, Trash2, Edit2, Check, X, RefreshCw } from 'lucide-react';
import {
  listDatabaseMemories,
  createDatabaseMemory,
  updateDatabaseMemory,
  deleteDatabaseMemory,
  getDatabaseStatus,
  getApi,
  type MemoryRecord,
  type DatabaseStatus,
} from '@/api';

type Props = {
  token: string;
};

export default function DatabaseMemoriesPanel({ token }: Props) {
  const [memories, setMemories] = useState<MemoryRecord[]>([]);
  const [status, setStatus] = useState<DatabaseStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      const dbStatus = await getDatabaseStatus(token);
      setStatus(dbStatus);
      if (dbStatus.ready) {
        const list = await listDatabaseMemories(token, 50, 0);
        setMemories(list);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load database memories.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [token]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newTitle.trim() || !newContent.trim()) return;
    try {
      const created = await createDatabaseMemory(token, newTitle.trim(), newContent.trim());
      setMemories(prev => [created, ...prev]);
      setNewTitle('');
      setNewContent('');
      setAdding(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create memory.');
    }
  }

  async function handleUpdate(m: MemoryRecord) {
    if (!editTitle.trim() || !editContent.trim()) return;
    try {
      const updated = await updateDatabaseMemory(token, m.id, m.version, editTitle.trim(), editContent.trim());
      setMemories(prev => prev.map(item => (item.id === m.id ? updated : item)));
      setEditingId(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update memory.');
    }
  }

  async function handleDelete(m: MemoryRecord) {
    if (!window.confirm(`Delete memory "${m.title}"?`)) return;
    try {
      await deleteDatabaseMemory(token, m.id, m.version);
      setMemories(prev => prev.filter(item => item.id !== m.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete memory.');
    }
  }

  async function handleExport() {
    try {
      const records = await getApi<MemoryRecord[]>('database/memories/export', token);
      const url = URL.createObjectURL(new Blob([JSON.stringify(records, null, 2)], { type: 'application/json' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'jarvis-memories.json';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not export memories.');
    }
  }

  return (
    <section className="database-memories-page">
      <div className="section-header">
        <div>
          <span className="eyebrow">POSTGRESQL RELATIONAL PERSISTENCE</span>
          <h1>Permanent Memory Notes</h1>
          <p className="subtle">Durable memories stored in PostgreSQL with Row-Level Security.</p>
        </div>
        <div className="header-actions">
          <button className="text-button" onClick={handleExport} disabled={!status?.ready}>Export memories</button>
          <button className="text-button" onClick={load} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'spin' : ''} /> Refresh
          </button>
          {status?.ready && !adding && (
            <button className="primary-button compact" onClick={() => setAdding(true)}>
              <Plus size={14} /> Add memory
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-banner" role="alert">{error}</div>}

      {!status?.configured ? (
        <div className="drawer-empty-state">
          <Database size={40} />
          <h2>PostgreSQL Not Configured</h2>
          <p>Set JARVIS_DB_URL, JARVIS_DB_USER, and JARVIS_DB_PASSWORD in your .env file to enable durable memories.</p>
        </div>
      ) : !status?.ready ? (
        <div className="drawer-empty-state">
          <Database size={40} />
          <h2>Database Offline</h2>
          <p>Database credentials are configured but the PostgreSQL server is not responding or schema is missing.</p>
        </div>
      ) : (
        <>
          {adding && (
            <form className="memory-card form-card" onSubmit={handleCreate}>
              <h3>New Database Memory</h3>
              <label>
                Title
                <input
                  value={newTitle}
                  onChange={e => setNewTitle(e.target.value)}
                  placeholder="e.g., Coding Preferences, API Keys Guide"
                  maxLength={200}
                  required
                />
              </label>
              <label>
                Content
                <textarea
                  value={newContent}
                  onChange={e => setNewContent(e.target.value)}
                  placeholder="Enter detailed facts or instructions (up to 8,000 characters)..."
                  maxLength={8000}
                  rows={5}
                  required
                />
              </label>
              <div className="form-card-actions">
                <button type="submit" className="primary-button compact">
                  Save to database
                </button>
                <button type="button" className="text-button" onClick={() => setAdding(false)}>
                  Cancel
                </button>
              </div>
            </form>
          )}

          {memories.length === 0 && !adding ? (
            <div className="drawer-empty-state">
              <Database size={36} />
              <p>No relational memories stored yet.</p>
              <button className="primary-button compact" onClick={() => setAdding(true)}>
                <Plus size={14} /> Create your first memory
              </button>
            </div>
          ) : (
            <div className="memories-grid">
              {memories.map(m => {
                const isEditing = editingId === m.id;
                return (
                  <article key={m.id} className="memory-card">
                    {isEditing ? (
                      <div className="memory-edit-form">
                        <label>
                          Title
                          <input
                            value={editTitle}
                            onChange={e => setEditTitle(e.target.value)}
                            maxLength={200}
                            required
                          />
                        </label>
                        <label>
                          Content
                          <textarea
                            value={editContent}
                            onChange={e => setEditContent(e.target.value)}
                            maxLength={8000}
                            rows={6}
                            required
                          />
                        </label>
                        <div className="memory-actions">
                          <button
                            className="primary-button compact"
                            onClick={() => void handleUpdate(m)}
                          >
                            <Check size={14} /> Save
                          </button>
                          <button
                            className="text-button"
                            onClick={() => setEditingId(null)}
                          >
                            <X size={14} /> Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <div className="memory-header">
                          <h3>{m.title}</h3>
                          <div className="memory-item-actions">
                            <button
                              className="icon-button"
                              title="Edit"
                              onClick={() => {
                                setEditingId(m.id);
                                setEditTitle(m.title);
                                setEditContent(m.content);
                              }}
                            >
                              <Edit2 size={13} />
                            </button>
                            <button
                              className="icon-button"
                              title="Delete"
                              onClick={() => void handleDelete(m)}
                            >
                              <Trash2 size={13} />
                            </button>
                          </div>
                        </div>
                        <p className="memory-content">{m.content}</p>
                        <div className="memory-footer">
                          <span>Version {m.version}</span>
                          <span>{new Date(m.updated_at).toLocaleDateString()}</span>
                        </div>
                      </>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </>
      )}
    </section>
  );
}
