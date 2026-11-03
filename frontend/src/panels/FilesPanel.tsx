import { useEffect, useRef, useState } from "react";
import { apiHeaders } from "@/api";

type Doc = { path: string; content: string; hash: string };
type Memory = { paths: string[]; notes: string; version: string };
type Proposal = {
  id: string;
  path: string;
  before: string;
  after: string;
  hash: string;
};
type Listing = {
  entries: { path: string; directory: boolean }[];
  truncated: boolean;
};
type Answer = { text: string; sources: { path: string; excerpt: string }[] };

export default function FilesPanel({ token }: { token: string }) {
  const [folder, setFolder] = useState("");
  const [listing, setListing] = useState<Listing>({
    entries: [],
    truncated: false,
  });
  const [memory, setMemory] = useState<Memory>({
    paths: [],
    notes: "",
    version: "0",
  });
  const [notes, setNotes] = useState("");
  const [doc, setDoc] = useState<Doc | null>(null);
  const [content, setContent] = useState("");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [instruction, setInstruction] = useState("");
  const [question, setQuestion] = useState("");
  const [questionPath, setQuestionPath] = useState("");
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const active = useRef<AbortController | null>(null);
  const [cancellable, setCancellable] = useState(false);
  useEffect(() => () => active.current?.abort(), []);
  async function api<T>(
    path: string,
    method = "GET",
    body?: unknown,
    signal?: AbortSignal,
  ): Promise<T> {
    const response = await fetch("/api/v1/files/" + path, {
      method,
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: signal ?? active.current?.signal,
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.message || `Request failed (${response.status})`);
    }
    return response.json();
  }
  async function run(work: () => Promise<void>, canCancel = false) {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setBusy(true);
    setCancellable(canCancel);
    setError("");
    setStatus("");
    try {
      await work();
    } catch (e) {
      if (controller.signal.aborted) setStatus("Request stopped.");
      else setError(e instanceof Error ? e.message : "File request failed");
    } finally {
      if (active.current === controller) {
        active.current = null;
        setBusy(false);
        setCancellable(false);
      }
    }
  }
  function updateMemory(value: Memory) {
    setMemory(value);
    setNotes(value.notes);
    setQuestionPath(current => value.paths.includes(current) ? current : "");
    setAnswer(null);
  }
  useEffect(() => {
    const c = new AbortController();
    Promise.all([
      api<Memory>("memory", "GET", undefined, c.signal),
      api<Listing>("browse", "GET", undefined, c.signal),
    ])
      .then(([m, l]) => {
        updateMemory(m);
        setListing(l);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(String(e));
      });
    return () => c.abort();
  }, [token]);
  async function browse(path: string) {
    setListing(await api<Listing>("browse?path=" + encodeURIComponent(path)));
    setFolder(path);
  }
  async function open(path: string) {
    const d = await api<Doc>("read?path=" + encodeURIComponent(path));
    setDoc(d);
    setContent(d.content);
    setProposal(null);
  }
  return (
    <section className="files-page">
      <span className="eyebrow">LOCAL FILES & MEMORY</span>
      <h1>Your files, with you in control.</h1>
      <p>
        Browse local drives. Index selected folders for answers. UTF-8 .txt and
        .md files under 16 KB and .docx/.pdf files under 2 MB (up to 16 KB extracted text)
        are supported. PDFs are limited to 50 pages with a text layer. Word and PDF files are read-only;
        system, hidden and credential paths are excluded.
      </p>
      {error && (
        <p role="alert" className="error-banner">
          {error}
        </p>
      )}
      {status && <p role="status">{status}</p>}
      <fieldset disabled={busy}>
        <legend>Browse and index</legend>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void run(() => browse(folder));
          }}
        >
          <label>
            Folder path
            <input
              value={folder}
              onChange={(e) => setFolder(e.target.value)}
              placeholder="D:\\Documents"
            />
          </label>
          <button>Open folder</button>
          <button type="button" onClick={() => void run(() => browse(""))}>
            Drives
          </button>
          <button
            type="button"
            disabled={!folder}
            onClick={() =>
              void run(async () => {
                updateMemory(
                  await api<Memory>("index", "POST", { path: folder }),
                );
                setStatus("Folder indexed. Unsupported files were skipped.");
              })
            }
          >
            Index this folder
          </button>
        </form>
        {listing.truncated && (
          <p>Showing a limited listing. Enter a more specific folder path.</p>
        )}
        <div className="file-list">
          {listing.entries.map((entry) => (
            <button
              key={entry.path}
              onClick={() =>
                void run(() =>
                  entry.directory ? browse(entry.path) : open(entry.path),
                )
              }
            >
              {entry.directory ? "Folder: " : "File: "}
              {entry.path}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset disabled={busy}>
        <legend>Persistent memory</legend>
        <label>
          Preferences and requirements (up to 1000 UTF-8 bytes)
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={1000}
            rows={3}
          />
        </label>
        <button
          onClick={() =>
            void run(async () => {
              updateMemory(
                await api<Memory>("memory", "PUT", {
                  notes,
                  version: memory.version,
                }),
              );
              setStatus("Memory saved locally.");
            })
          }
        >
          Save memory
        </button>
        <button
          onClick={() =>
            void run(async () => updateMemory(await api<Memory>("memory")))
          }
        >
          Reload memory
        </button>
        <details>
          <summary>{memory.paths.length} indexed files</summary>
          {memory.paths.map((path) => (
            <div className="indexed-file" key={path}>
              <span>{path}</span>
              <button
                onClick={() =>
                  void run(async () =>
                    updateMemory(
                      await api<Memory>("forget", "POST", {
                        path,
                        version: memory.version,
                      }),
                    ),
                  )
                }
              >
                Forget from index
              </button>
            </div>
          ))}
        </details>
      </fieldset>
      <fieldset disabled={busy}>
        <legend>Ask your files</legend>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              async () =>
                setAnswer(
                  await api<Answer>("answer", "POST", { message: question, path: questionPath || null }),
                ),
              true,
            );
          }}
        >
          <label>
            Search in
            <select value={questionPath} onChange={(e) => {
              setQuestionPath(e.target.value);
              setAnswer(null);
            }}>
              <option value="">All indexed files & memory</option>
              {memory.paths.map(path => <option key={path} value={path}>{path}</option>)}
            </select>
          </label>
          <label>
            Question and requirements
            <textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              maxLength={300}
              rows={2}
              required
              placeholder="What does my project require? Answer briefly."
            />
          </label>
          <button disabled={!question.trim()}>
            {questionPath ? "Answer from selected file" : "Answer from files & memory"}
          </button>
        </form>
        {answer && (
          <article className="file-answer">
            <p>{answer.text}</p>
            <ol>
              {answer.sources.map((s, i) => (
                <li key={i}>
                  <strong>
                    [{i + 1}] {s.path}
                  </strong>
                  <p>{s.excerpt}</p>
                </li>
              ))}
            </ol>
          </article>
        )}
      </fieldset>
      {doc && /\.(docx|pdf)$/i.test(doc.path) && (
        <fieldset disabled={busy}>
          <legend>Preview: {doc.path}</legend>
          <p>{doc.path.toLowerCase().endsWith(".pdf")
            ? "Extracted PDF text only. Images and page layout are omitted; OCR is not supported."
            : "Main document text only, including tables. Images, headers, footers and layout are omitted."}
            {" "}References use extracted text line numbers, not page numbers. Edit the original and reindex the folder.</p>
          <pre className="file-editor">{doc.content}</pre>
          <button onClick={() => void run(() => open(doc.path))}>Reload file</button>
        </fieldset>
      )}
      {doc && !/\.(docx|pdf)$/i.test(doc.path) && (
        <fieldset disabled={busy}>
          <legend>Edit: {doc.path}</legend>
          <label>
            File text
            <textarea
              className="file-editor"
              value={content}
              onChange={(e) => {
                setContent(e.target.value);
                setProposal(null);
              }}
              maxLength={16000}
              rows={12}
            />
          </label>
          <button
            onClick={() =>
              void run(async () =>
                setProposal(
                  await api<Proposal>("propose", "POST", {
                    path: doc.path,
                    hash: doc.hash,
                    content,
                  }),
                ),
              )
            }
          >
            Review manual edit
          </button>
          <button onClick={() => void run(() => open(doc.path))}>
            Reload file
          </button>
          <label>
            Ask AI for an edit (files up to 600 UTF-8 bytes)
            <input
              value={instruction}
              onChange={(e) => setInstruction(e.target.value)}
              maxLength={200}
            />
          </label>
          <button
            disabled={!instruction.trim()}
            onClick={() =>
              void run(
                async () =>
                  setProposal(
                    await api<Proposal>("suggest", "POST", {
                      path: doc.path,
                      hash: doc.hash,
                      instruction,
                    }),
                  ),
                true,
              )
            }
          >
            Suggest edit
          </button>
          {proposal && (
            <div className="edit-review">
              <h2>Review before applying</h2>
              <p>
                Check the proposed text against your requirements. Applying
                saves a backup of the original.
              </p>
              <h3>Before</h3>
              <pre>{proposal.before}</pre>
              <h3>After</h3>
              <pre>{proposal.after}</pre>
              <button
                onClick={() =>
                  void run(async () => {
                    const d = await api<Doc>("apply", "POST", {
                      id: proposal.id,
                    });
                    setDoc(d);
                    setContent(d.content);
                    setProposal(null);
                    setStatus("Edit applied. Original saved in local backups.");
                  })
                }
              >
                Apply reviewed edit
              </button>
              <button onClick={() => setProposal(null)}>
                Discard proposal
              </button>
            </div>
          )}
        </fieldset>
      )}
      {busy && (
        <p role="status">
          Working… File answers and suggestions may need time to load the model.
          {cancellable && (
            <button onClick={() => active.current?.abort()}>
              Stop request
            </button>
          )}
        </p>
      )}
    </section>
  );
}
