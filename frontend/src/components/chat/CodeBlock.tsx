import { useState, memo, useMemo } from "react";
import Prism from "prismjs";
import "prismjs/themes/prism-tomorrow.css";
// Languages
import "prismjs/components/prism-javascript";
import "prismjs/components/prism-typescript";
import "prismjs/components/prism-jsx";
import "prismjs/components/prism-tsx";
import "prismjs/components/prism-python";
import "prismjs/components/prism-java";
import "prismjs/components/prism-json";
import "prismjs/components/prism-bash";
import "prismjs/components/prism-sql";
import "prismjs/components/prism-markdown";
import "prismjs/components/prism-css";
import "prismjs/components/prism-yaml";
import { Check, Copy, Code2 } from "lucide-react";

const LANG_ALIASES: Record<string, string> = {
  js: "javascript",
  ts: "typescript",
  py: "python",
  sh: "bash",
  shell: "bash",
  zsh: "bash",
  yml: "yaml",
  html: "markup",
  xml: "markup",
  md: "markdown",
  txt: "text",
};

interface CodeBlockProps {
  language: string;
  code: string;
}

const CodeBlock = memo(function CodeBlock({ language, code }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const rawLang = (language || "text").toLowerCase().trim();
  const lang = LANG_ALIASES[rawLang] || rawLang;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback if clipboard API fails
      const el = document.createElement("textarea");
      el.value = code;
      document.body.appendChild(el);
      el.select();
      document.execCommand("copy");
      document.body.removeChild(el);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const highlightedHtml = useMemo(() => {
    try {
      const grammar = Prism.languages[lang] || Prism.languages.text;
      if (!grammar) {
        return Prism.util.encode(code);
      }
      return Prism.highlight(code, grammar, lang);
    } catch {
      return Prism.util.encode(code);
    }
  }, [code, lang]);

  return (
    <div className="code-block-wrapper">
      <div className="code-block-header">
        <div className="code-lang-label">
          <Code2 size={13} />
          <span>{rawLang || "code"}</span>
        </div>
        <button
          type="button"
          className="code-copy-btn"
          onClick={handleCopy}
          title={copied ? "Copied to clipboard!" : "Copy code"}
        >
          {copied ? <Check size={13} style={{ color: "#34d399" }} /> : <Copy size={13} />}
          <span>{copied ? "Copied!" : "Copy"}</span>
        </button>
      </div>
      <pre className={`code-block-pre language-${lang}`}>
        <code
          className={`language-${lang}`}
          dangerouslySetInnerHTML={{ __html: highlightedHtml }}
        />
      </pre>
    </div>
  );
});

export default CodeBlock;
