import { Icon, type IconName } from "./icons";

/** The launcher's pages, one per sidebar button. */
export type Page = "play" | "mods" | "news" | "settings";

/** Each page's name and icon, in the sidebar's order; Settings sits apart at the bottom. */
export const PAGES: Record<Page, { label: string; icon: IconName }> = {
  play: { label: "Play", icon: "play" },
  mods: { label: "Mods", icon: "mods" },
  news: { label: "News", icon: "news" },
  settings: { label: "Settings", icon: "settings" },
};

/**
 * Icons only, with each name shown on hover: the pages are few and never
 * change, so after the first look the icon is enough, and the space goes to
 * the page instead.
 */
export function Sidebar(props: { page: Page; onOpen: (page: Page) => void }) {
  return (
    <nav className="sidebar">
      {(Object.keys(PAGES) as Page[]).map((page) => (
        <button
          key={page}
          className={`nav${page === "settings" ? " nav-bottom" : ""}`}
          data-label={PAGES[page].label}
          aria-label={PAGES[page].label}
          aria-current={props.page === page ? "page" : undefined}
          onClick={() => props.onOpen(page)}
        >
          <Icon name={PAGES[page].icon} />
        </button>
      ))}
    </nav>
  );
}

/** A page with nothing on it yet, saying so plainly rather than looking broken. */
export function EmptyPage(props: { page: Page; headline: string; detail: string }) {
  return (
    <section className="page">
      <h2 className="page-title">{PAGES[props.page].label}</h2>
      <div className="empty-state">
        <Icon name={PAGES[props.page].icon} />
        <b>{props.headline}</b>
        <span>{props.detail}</span>
      </div>
    </section>
  );
}
