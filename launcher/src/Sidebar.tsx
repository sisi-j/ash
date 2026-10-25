import { Icon, type IconName } from "./icons";

/** The launcher's pages, one per sidebar button. */
export type Page = "play" | "wardrobe" | "mods" | "news" | "settings";

/** Each page's name and icon, in the sidebar's order; Settings sits apart at the bottom. */
export const PAGES: Record<Page, { label: string; icon: IconName }> = {
  play: { label: "Play", icon: "play" },
  wardrobe: { label: "Wardrobe", icon: "wardrobe" },
  mods: { label: "Mods", icon: "mods" },
  news: { label: "News", icon: "news" },
  settings: { label: "Settings", icon: "settings" },
};

/**
 * Icons only, with each name shown on hover: the pages are few and never
 * change, so after the first look the icon is enough, and the space goes to
 * the page instead.
 */
export function Sidebar(props: {
  page: Page;
  onOpen: (page: Page) => void;
  unread?: Page[];
  /** "Send feedback", when there's a link to send it to (spec 0005). */
  onFeedback?: (() => void) | undefined;
}) {
  const button = (page: Page, bottom: boolean) => (
    <button
      key={page}
      className={`nav${bottom ? " nav-bottom" : ""}`}
      data-label={PAGES[page].label}
      data-tour={page}
      aria-label={props.unread?.includes(page) ? `${PAGES[page].label}, something new` : PAGES[page].label}
      aria-current={props.page === page ? "page" : undefined}
      onClick={() => props.onOpen(page)}
    >
      <Icon name={PAGES[page].icon} />
      {props.unread?.includes(page) && <span className="nav-dot" aria-hidden />}
    </button>
  );
  // The pages, then at the bottom "Send feedback" (when there's a link), then Settings.
  return (
    <nav className="sidebar">
      {(Object.keys(PAGES) as Page[]).filter((page) => page !== "settings").map((page) => button(page, false))}
      {props.onFeedback && (
        <button
          className="nav nav-bottom"
          data-label="Send feedback"
          data-tour="feedback"
          aria-label="Send feedback"
          onClick={props.onFeedback}
        >
          <Icon name="feedback" />
        </button>
      )}
      {button("settings", !props.onFeedback)}
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
