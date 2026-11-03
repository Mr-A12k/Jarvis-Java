// Run with Node.js. The secret belongs in ignored .env, never in browser code.
const { randomBytes } = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const envPath = path.resolve(__dirname, "..", ".env");
const exists = fs.existsSync(envPath);
const original = exists ? fs.readFileSync(envPath, "utf8") : "";
const entries = [...original.matchAll(/^\s*JARVIS_API_TOKEN\s*=([^\r\n]*)/gm)];
if (entries.length > 1) {
  throw new Error(
    "Multiple JARVIS_API_TOKEN entries found. Keep one entry in .env and retry.",
  );
}
let current = entries[0]?.[1].trim() ?? "";
if (
  (current.startsWith('"') && current.endsWith('"')) ||
  (current.startsWith("'") && current.endsWith("'"))
)
  current = current.slice(1, -1);
if (current) {
  if (current.length < 32)
    throw new Error(
      "Existing token is too short. Clear its value in .env and retry.",
    );
  console.log(
    "Existing JARVIS_API_TOKEN preserved in .env. Token not printed.",
  );
} else {
  const line = `JARVIS_API_TOKEN=${randomBytes(32).toString("base64url")}`;
  const updated = entries.length
    ? original.replace(/^\s*JARVIS_API_TOKEN\s*=[^\r\n]*/m, line)
    : original +
      (original && !original.endsWith("\n") ? "\r\n" : "") +
      line +
      "\r\n";
  fs.writeFileSync(envPath, updated, {
    encoding: "utf8",
    flag: exists ? "w" : "wx",
    mode: 0o600,
  });
  console.log(
    "Secure JARVIS_API_TOKEN generated and saved to .env. Token not printed.",
  );
}
console.log(
  "Restart the backend to load .env. Copy the token value into the local connection field.",
);
