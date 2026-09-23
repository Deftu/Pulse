# This file

**No agent may edit `AGENTS.md` without first asking the user and being told it may.** Ask, wait
for an answer, then edit — a standing instruction elsewhere in the session does not count, and
neither does a change that looks obviously correct. Suggest the edit in your response instead and
let the user decide.

**This file holds rules and conventions only.** Nothing that describes the current state of the
tree belongs here — not what is built, not what is broken, not what has yet to be written. State
goes in `.agents/`; a rule is a thing that stays true after the state changes. That constraint
governs every future edit to this file, not just the ones made so far: if the sentence would need
rewriting once someone fixes something, it is not a rule.

# Response style

- Keep responses short and scannable — ADHD-friendly reading.
- Bullet points over paragraphs.
- Lead with the actionable part first.
- Skip preamble and unnecessary explanation.
- Don't restate what the user already said.

# `.agents/`

`.agents/` (gitignored, local only) is the project's local scratchpad, testing ground, progress
tracker, and ongoing plan of record. `README.md` inside it indexes the contents.

- Keep files concise — high-signal over exhaustive.
- Aggressively truncate: overwrite or archive stale history rather than appending to long files.
- Use `.agents/NEXT-SESSION.md` for session handoffs: current state, open blockers, exact next
  steps.
- Source is the truth once code exists. Never point at `.agents/` from code, a comment, a commit
  message, or a PR description — it is context nobody reading the code has, and it is not
  committed. Restate the reasoning inline instead.

# Testing

- **Write the test first.** Red before green: a failing test naming the behaviour, then the code
  that satisfies it.
- A test that has never failed has not been shown to discriminate. Make it fail on purpose before
  trusting it.
- Bug fixes start with the test that reproduces the bug.

# Comments

Comment the trade-offs, not the code. Where a comment exists it is because the decision looks
arbitrary without the reasoning — a platform quirk, a concurrency/lifecycle constraint, a
deliberate refusal to support something. That is a high bar, not a default.

- Never restate what the code does. Most functions need no comment at all.
- Never describe the change you just made, or what the code "used to" do.
- Never point at `.agents/` or any other internal planning doc from code or a commit message.
  Restate the reasoning inline instead.
