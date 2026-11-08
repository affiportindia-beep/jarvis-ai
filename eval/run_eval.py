#!/usr/bin/env python3
"""Desktop quality check for JARVIS X models (CI only, never on the phone).

Talks to a local llama-server (same llama.cpp tag as the app) on 127.0.0.1, with the app's
exact system prompt and language hint, and writes a Markdown report. It checks whether each
answer is in the right script; humans judge the content. Speeds here are a desktop CPU's, NOT
the phone's: phone numbers come from the in-app benchmark.
"""
import json, sys, time, urllib.request, datetime, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
HINTS = {  # must match com.jarvisx.core.lang.ReplyLanguage.instruction
    "ENGLISH": "Reply in English.",
    "HINDI": "Reply in Hindi using Devanagari script.",
    "HINGLISH": "Reply in Hinglish: Hindi words written in Roman (English) letters, mixed with English as the user does.",
}

def system_prompt(model_name):
    t = (ROOT / "core/src/main/resources/jarvisx/system_prompt.txt").read_text(encoding="utf-8")
    today = datetime.date.today().strftime("%-d %B %Y")
    return t.replace("{MODEL}", model_name).replace("{DATE}", today).strip()

def script_ok(lang, text):
    deva = sum(1 for c in text if "ऀ" <= c <= "ॿ")
    latin = sum(1 for c in text if c.isascii() and c.isalpha())
    total = max(1, deva + latin)
    if lang == "HINDI":
        return deva / total >= 0.5
    return deva / total <= 0.05

def ask(port, system, user):
    body = json.dumps({
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "temperature": 0.7, "top_p": 0.8, "top_k": 20, "max_tokens": 320,
        "chat_template_kwargs": {"enable_thinking": False},
    }).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{port}/v1/chat/completions", body, {"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=900) as r:
        data = json.load(r)
    text = data["choices"][0]["message"]["content"]
    tps = data.get("timings", {}).get("predicted_per_second", 0.0)
    return text, tps, time.time() - t0

def main():
    port, model_name, out = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    system = system_prompt(model_name)
    rows, script_pass, expect_pass, expect_total = [], 0, 0, 0
    prompts = [json.loads(l) for l in (ROOT / "eval/prompts.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    for p in prompts:
        user = f"{p['prompt']}\n\n({HINTS[p['lang']]})"
        try:
            text, tps, secs = ask(port, system, user)
        except Exception as e:  # report, don't hide
            text, tps, secs = f"ERROR: {e}", 0.0, 0.0
        text = text.replace("<think>", "").replace("</think>", "").strip()
        ok = (not text.startswith("ERROR:")) and script_ok(p["lang"], text)
        script_pass += ok
        exp = p.get("expect") or ""
        exp_ok = None
        if exp:
            expect_total += 1
            # Count Devanagari digits (३००) and Indian grouping (1,20,000) as the same number.
            norm = text.translate(str.maketrans("०१२३४५६७८९", "0123456789")).replace(",", "")
            exp_ok = exp.lower() in norm.lower()
            expect_pass += exp_ok
        rows.append((p, text, ok, exp_ok, tps, secs))
        print(f"[{p['id']}] script={'ok' if ok else 'WRONG'} expect={exp_ok} {tps:.1f} tok/s", flush=True)
        print("    A: " + text.replace("\n", " / ")[:600], flush=True)

    with open(out, "w", encoding="utf-8") as f:
        f.write(f"## {model_name}\n\n")
        f.write(f"Right script: **{script_pass}/{len(prompts)}** · expected keyword present: **{expect_pass}/{expect_total}**\n\n")
        f.write("Speeds are from a GitHub desktop CPU, not a phone.\n\n")
        for p, text, ok, exp_ok, tps, secs in rows:
            mark = "✅" if ok else "❌ wrong script"
            e = "" if exp_ok is None else (" · keyword ✅" if exp_ok else f" · keyword ❌ (`{p['expect']}`)")
            f.write(f"<details><summary><b>{p['id']}</b> ({p['lang']}) {mark}{e} · {tps:.1f} tok/s</summary>\n\n")
            f.write(f"**Q:** {p['prompt']}\n\n**A:**\n\n{text}\n\n</details>\n\n")

if __name__ == "__main__":
    main()
