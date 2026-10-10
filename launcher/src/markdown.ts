/**
 * The Markdown subset news posts are written in (spec 0004, #126):
 * headings, paragraphs, bullet and numbered lists, **bold**, *italic* or
 * _italic_, and [links](https://...).
 *
 * Parsed into a tree that React draws - never into an HTML string - so
 * nothing a post contains can become markup. Anything outside the subset is
 * shown as the text it is. The same rules as the backend's admin preview.
 */

export type Inline =
  | { type: "text"; text: string }
  | { type: "strong"; text: string }
  | { type: "em"; text: string }
  | { type: "link"; text: string; href: string };

export type Block =
  | { type: "heading"; level: 2 | 3; content: Inline[] }
  | { type: "paragraph"; content: Inline[] }
  | { type: "list"; ordered: boolean; items: Inline[][] };

const HEADING = /^(#{1,3})\s+(.*)$/;
const LIST_ITEM = /^\s*([-*]|\d+\.)\s+/;
const ORDERED = /^\s*\d+\./;
const INLINE = /(\*\*[^*]+\*\*|\*[^*]+\*|_[^_]+_|\[[^\]]+\]\(https:\/\/[^)\s]+\))/;

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  for (const part of text.split(INLINE)) {
    if (!part) continue;
    let m: RegExpExecArray | null;
    if ((m = /^\*\*([^*]+)\*\*$/.exec(part))) out.push({ type: "strong", text: m[1]! });
    else if ((m = /^(?:\*([^*]+)\*|_([^_]+)_)$/.exec(part))) out.push({ type: "em", text: (m[1] ?? m[2])! });
    else if ((m = /^\[([^\]]+)\]\((https:\/\/[^)\s]+)\)$/.exec(part))) out.push({ type: "link", text: m[1]!, href: m[2]! });
    else out.push({ type: "text", text: part });
  }
  return out;
}

export function parseMarkdown(markdown: string): Block[] {
  const lines = markdown.replace(/\r/g, "").split("\n");
  const blocks: Block[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i]!;
    if (!line.trim()) {
      i++;
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ type: "heading", level: heading[1]!.length === 3 ? 3 : 2, content: parseInline(heading[2]!) });
      i++;
      continue;
    }
    if (LIST_ITEM.test(line)) {
      const ordered = ORDERED.test(line);
      const items: Inline[][] = [];
      while (i < lines.length && LIST_ITEM.test(lines[i]!)) {
        items.push(parseInline(lines[i]!.replace(LIST_ITEM, "")));
        i++;
      }
      blocks.push({ type: "list", ordered, items });
      continue;
    }
    const paragraph: string[] = [];
    while (i < lines.length && lines[i]!.trim() && !HEADING.test(lines[i]!) && !LIST_ITEM.test(lines[i]!)) {
      paragraph.push(lines[i]!.trim());
      i++;
    }
    blocks.push({ type: "paragraph", content: parseInline(paragraph.join(" ")) });
  }
  return blocks;
}
