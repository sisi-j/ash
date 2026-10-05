import { Icon, type IconName } from "./icons";

/** The launcher's pages, one per sidebar button. */
export type Page = "play" | "mods" | "news" | "settings";

const TOP: [Page, string, IconName][] = [
  ["play", "Play", "play"],
  ["mods", "Mods", "mods"],
  ["news", "News", "news"],
];

/**
 * Icons only, with each name shown on hover: the pages are few and never
 * change, so after the first look the icon is enough, and the space goes to
 * the page instead.
 */
export function Sidebar(props: { page: Page; onGo: (page: Page) => void }) {
  const button = ([page, label, icon]: [Page, string, IconName]) => (
    <button
      key={page}
      className={`nav${page === "settings" ? " nav-bottom" : ""}`}
      data-label={label}
      aria-label={label}
      aria-current={props.page === page ? "page" : undefined}
      onClick={() => props.onGo(page)}
    >
      <Icon name={icon} />
    </button>
  );

  return (
    <nav className="sidebar">
      {TOP.map(button)}
      {button(["settings", "Settings", "settings"])}
    </nav>
  );
}

/** A page with nothing on it yet, saying so plainly rather than looking broken. */
export function EmptyPage(props: { title: string; icon: IconName; headline: string; detail: string }) {
  return (
    <section className="page">
      <h2 className="page-title">{props.title}</h2>
      <div className="empty-state">
        <Icon name={props.icon} />
        <b>{props.headline}</b>
        <span>{props.detail}</span>
      </div>
    </section>
  );
}
