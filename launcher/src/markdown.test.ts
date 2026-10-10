// `npm test`: node's own runner, on node's own TypeScript stripping.
import assert from "node:assert/strict";
import { test } from "node:test";
import { parseInline, parseMarkdown } from "./markdown.ts";

test("headings, paragraphs and lists become their blocks", () => {
  const blocks = parseMarkdown("## What's new\n\nOne line\nwraps on.\n\n- first\n- second\n\n1. one\n2. two\n### Small");
  assert.deepEqual(
    blocks.map((b) => b.type),
    ["heading", "paragraph", "list", "list", "heading"],
  );
  assert.deepEqual(blocks[1], { type: "paragraph", content: [{ type: "text", text: "One line wraps on." }] });
  assert.equal(blocks[2]!.type === "list" && blocks[2]!.ordered, false);
  assert.equal(blocks[3]!.type === "list" && blocks[3]!.ordered, true);
  assert.equal(blocks[4]!.type === "heading" && blocks[4]!.level, 3);
});

test("bold, italic and https links are recognised inline", () => {
  assert.deepEqual(parseInline("a **b** *c* _d_ [e](https://ashlauncher.com/x)"), [
    { type: "text", text: "a " },
    { type: "strong", text: "b" },
    { type: "text", text: " " },
    { type: "em", text: "c" },
    { type: "text", text: " " },
    { type: "em", text: "d" },
    { type: "text", text: " " },
    { type: "link", text: "e", href: "https://ashlauncher.com/x" },
  ]);
});

test("a link that isn't https stays plain text", () => {
  for (const unsafe of ["[x](javascript:alert(1))", "[x](http://example.com)", "[x](file:///C:/)"]) {
    assert.deepEqual(parseInline(unsafe), [{ type: "text", text: unsafe }], unsafe);
  }
});

test("HTML in a post is text, never markup", () => {
  const blocks = parseMarkdown('<img src=x onerror="alert(1)"> <script>alert(1)</script>');
  assert.deepEqual(blocks, [
    {
      type: "paragraph",
      content: [{ type: "text", text: '<img src=x onerror="alert(1)"> <script>alert(1)</script>' }],
    },
  ]);
});

test("Windows line endings and blank input are fine", () => {
  assert.deepEqual(parseMarkdown(""), []);
  assert.equal(parseMarkdown("a\r\n\r\nb").length, 2);
});
