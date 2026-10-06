//! The servers the player joined, with when: the record ash's client keeps,
//! because the game's own list records no recency (`docs/research/0008`).
//!
//! `ash/recent-servers.json` in the game directory. The client writes it on
//! every join, however the player joined; the launcher only reads it, as it
//! only reads the load report. It holds addresses and times and nothing else,
//! and an entry that is not an address the game could connect to is dropped.

use std::path::Path;

use serde::Deserialize;

use crate::server_list::Address;

const RELATIVE_PATH: &str = "ash/recent-servers.json";

/// Longer than any host name DNS allows, with a port.
const MAX_ADDRESS: usize = 261;

#[derive(Deserialize)]
struct File {
    #[serde(default)]
    servers: Vec<Entry>,
}

#[derive(Deserialize)]
struct Entry {
    address: String,
    joined_ms: u64,
}

/// One join on record.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Join {
    pub(crate) address: String,
    pub(crate) joined_ms: u64,
}

/// Why a record that exists could not be read, by kind and place only: the
/// file is in a folder the player can edit.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct Unreadable {
    pub(crate) line: usize,
    pub(crate) column: usize,
}

/// The joins on record, most recent first. None when the client has not
/// recorded any - a vanilla instance, or an ash one that has joined nothing.
pub(crate) fn read(game_directory: &Path) -> Result<Vec<Join>, Unreadable> {
    let Ok(raw) = std::fs::read(game_directory.join(RELATIVE_PATH)) else {
        return Ok(Vec::new());
    };
    let file: File = serde_json::from_slice(&raw)
        .map_err(|e| Unreadable { line: e.line(), column: e.column() })?;
    let mut joins: Vec<Join> = file
        .servers
        .into_iter()
        .filter(|e| e.address.len() <= MAX_ADDRESS && Address::parse(&e.address).is_some())
        .map(|e| Join { address: e.address.trim().to_owned(), joined_ms: e.joined_ms })
        .collect();
    // The client writes them in order; a file edited by hand may not be.
    joins.sort_by_key(|join| std::cmp::Reverse(join.joined_ms));
    Ok(joins)
}

/// One server however its address was written: case, spaces and a trailing
/// dot aside, as the client compares them.
pub(crate) fn same(a: &str, b: &str) -> bool {
    let normal = |address: &str| address.trim().trim_end_matches('.').to_lowercase();
    normal(a) == normal(b)
}
