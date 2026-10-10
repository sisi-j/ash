import { useState } from "react";
import { api, type News, type NewsKind, type NewsPost } from "./api";
import { type Block, type Inline, parseMarkdown } from "./markdown";
import { PAGES } from "./Sidebar";

const KIND_LABELS: Record<NewsKind, string> = { news: "News", patch_notes: "Patch notes" };
type Filter = "all" | NewsKind;
const FILTERS: { value: Filter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "news", label: "News" },
  { value: "patch_notes", label: "Patch notes" },
];

/**
 * News and patch notes from ash (spec 0004), newest first. A post's body is
 * the agreed Markdown subset, drawn from a parsed tree: nothing in a post
 * becomes markup, and only https links open, in the browser.
 */
export function NewsPage(props: { news: News | null }) {
  const [filter, setFilter] = useState<Filter>("all");
  const posts = (props.news?.posts ?? []).filter((p) => filter === "all" || p.kind === filter);

  return (
    <section className="page news-page">
      <h2 className="page-title">{PAGES.news.label}</h2>
      <div className="chips" role="radiogroup" aria-label="Show">
        {FILTERS.map((choice) => (
          <button
            key={choice.value}
            className={`chip${filter === choice.value ? " is-selected" : ""}`}
            role="radio"
            aria-checked={filter === choice.value}
            onClick={() => setFilter(choice.value)}
          >
            {choice.label}
          </button>
        ))}
      </div>

      {props.news?.offline && (
        <p className="notice" role="status">
          {props.news.posts.length > 0
            ? "ash's servers can't be reached, so this is the news ash last saw."
            : "ash's servers can't be reached right now. News will show here when they can."}
        </p>
      )}

      {props.news && posts.length === 0 && !(props.news.offline && props.news.posts.length === 0) && (
        <p className="notice">{filter === "all" ? "No news yet." : `No ${KIND_LABELS[filter].toLowerCase()} yet.`}</p>
      )}

      <div className="news-posts">
        {posts.map((post) => (
          <Post key={post.id} post={post} />
        ))}
      </div>
    </section>
  );
}

function Post(props: { post: NewsPost }) {
  const { post } = props;
  return (
    <article className="news-post">
      {post.cover_url && <img className="news-cover" src={post.cover_url} alt="" />}
      <div className="news-meta">
        <span className={`news-kind news-kind-${post.kind}`}>{KIND_LABELS[post.kind]}</span>
        <time dateTime={new Date(post.published_at).toISOString()}>
          {new Date(post.published_at).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" })}
        </time>
      </div>
      <h3 className="news-title">{post.title}</h3>
      <div className="news-body">
        {parseMarkdown(post.body).map((block, i) => (
          <BlockView key={i} block={block} />
        ))}
      </div>
    </article>
  );
}

function BlockView(props: { block: Block }) {
  const { block } = props;
  switch (block.type) {
    case "heading":
      return block.level === 2 ? (
        <h4>
          <Inlines content={block.content} />
        </h4>
      ) : (
        <h5>
          <Inlines content={block.content} />
        </h5>
      );
    case "paragraph":
      return (
        <p>
          <Inlines content={block.content} />
        </p>
      );
    case "list": {
      const items = block.items.map((item, i) => (
        <li key={i}>
          <Inlines content={item} />
        </li>
      ));
      return block.ordered ? <ol>{items}</ol> : <ul>{items}</ul>;
    }
  }
}

function Inlines(props: { content: Inline[] }) {
  return (
    <>
      {props.content.map((part, i) => {
        switch (part.type) {
          case "text":
            return <span key={i}>{part.text}</span>;
          case "strong":
            return <strong key={i}>{part.text}</strong>;
          case "em":
            return <em key={i}>{part.text}</em>;
          case "link":
            return (
              <a
                key={i}
                href={part.href}
                onClick={(e) => {
                  e.preventDefault();
                  void api.openNewsLink(part.href);
                }}
              >
                {part.text}
              </a>
            );
        }
      })}
    </>
  );
}
