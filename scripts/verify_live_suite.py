import urllib.request, json, sys, time, subprocess

sys.stdout.reconfigure(encoding='utf-8')

def chat(msg, history=None):
    if history is None:
        history = []
    payload = {'message': msg, 'history': history}
    data = json.dumps(payload).encode('utf-8')
    headers = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'X-Jarvis-Local': '1'
    }
    req = urllib.request.Request('http://127.0.0.1:18080/api/v1/chat', data=data, headers=headers)
    resp_text = []
    with urllib.request.urlopen(req, timeout=30) as r:
        for line in r:
            decoded = line.decode('utf-8', errors='replace')
            if decoded.startswith('data:'):
                try:
                    chunk = json.loads(decoded[5:].strip())
                    resp_text.append(chunk.get('text', ''))
                except:
                    pass
    full = "".join(resp_text)
    print(f"USER: {msg}")
    print(f"JARVIS: {full}")
    print("-" * 50)
    return full

print("=== TEST 1: Launch Notepad ===")
chat("open notepad")
time.sleep(1)

# Verify notepad is running
tasks = subprocess.check_output(['tasklist', '/FI', 'IMAGENAME eq notepad.exe']).decode(errors='ignore')
print("Notepad running check:", "notepad.exe" in tasks)

print("\n=== TEST 2: Close Notepad ===")
chat("close notepad")
time.sleep(1)

# Verify notepad is terminated
tasks2 = subprocess.check_output(['tasklist', '/FI', 'IMAGENAME eq notepad.exe']).decode(errors='ignore')
print("Notepad running check after close:", "notepad.exe" in tasks2)

print("\n=== TEST 3: Launch Notepad then 'close the opened application' ===")
chat("open notepad")
time.sleep(1)
chat("close the opened application")
time.sleep(1)
tasks3 = subprocess.check_output(['tasklist', '/FI', 'IMAGENAME eq notepad.exe']).decode(errors='ignore')
print("Notepad running check after 'close the opened application':", "notepad.exe" in tasks3)

print("\n=== TEST 4: General conversational prompt (checks for phantom tool calls and repetition) ===")
joke_resp = chat("tell me a short joke")
print("Contains [TOOL_CALL: ", "[TOOL_CALL:" in joke_resp)

print("\n=== TEST 5: Tamil close command ===")
chat("நோட்பேடை திற")
time.sleep(1)
chat("நோட்பேடை மூடு")
time.sleep(1)
tasks4 = subprocess.check_output(['tasklist', '/FI', 'IMAGENAME eq notepad.exe']).decode(errors='ignore')
print("Notepad running check after Tamil close:", "notepad.exe" in tasks4)

print("\n=== TEST 6: Meeting Notes Persistence ===")
notes_resp = chat("take meeting notes: discussed Q4 engineering goals and server migration")
import glob, os
note_files = glob.glob(os.path.join(os.path.dirname(__file__), "..", "data", "notes", "meeting_notes_*.txt"))
if note_files:
    latest_note = max(note_files, key=os.path.getmtime)
    with open(latest_note, "r", encoding="utf-8") as f:
        content = f.read()
    print("Latest note file exists:", os.path.exists(latest_note))
    print("Contains dictated text:", "discussed Q4 engineering goals and server migration" in content)
else:
    print("No meeting note files found.")

# Clean up any lingering notepad process
subprocess.run(['taskkill', '/IM', 'notepad.exe', '/F'], capture_output=True)

print("\n=== TEST 7: Segment Router Introspection ===")
try:
    seg_req = urllib.request.Request('http://127.0.0.1:18080/api/v1/chat/segments', headers={'X-Jarvis-Local': '1'})
    with urllib.request.urlopen(seg_req, timeout=5) as r:
        segments = json.loads(r.read().decode('utf-8'))
        research_seg = next((s for s in segments if s.get('id') == 'RESEARCH'), None)
        print("RESEARCH segment present:", research_seg is not None)
        if research_seg:
            print("RESEARCH default model:", research_seg.get('defaultModel'))
            print("RESEARCH active model:", research_seg.get('activeModel'))
            print("Specialized installed:", research_seg.get('isSpecializedInstalled'))
except Exception as e:
    print("Segment check failed:", e)

print("\n=== ALL ACCEPTANCE TESTS FINISHED ===")
