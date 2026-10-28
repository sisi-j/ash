import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "./api";

/**
 * Whether this is a test build, with the beta switch on (spec 0006,
 * ADR-0029). The beta phases' work in the launcher shows only when it is:
 *
 *     const beta = useBeta();
 *     {beta && <NewThing />}
 *
 * Off until the launcher has answered, so a release build never shows beta
 * work, even for a moment.
 */
const BetaBuild = createContext(false);

export const useBeta = () => useContext(BetaBuild);

/** Asks the launcher once, for everything under it. */
export function BetaBuildProvider(props: { children: ReactNode }) {
  const [beta, setBeta] = useState(false);
  useEffect(() => {
    api.betaBuild().then(setBeta).catch(() => setBeta(false));
  }, []);
  return <BetaBuild.Provider value={beta}>{props.children}</BetaBuild.Provider>;
}
