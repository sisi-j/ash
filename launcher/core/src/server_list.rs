//! An instance's servers: the game's own list, and each server's status.
//!
//! The list is `servers.dat` in the game directory, written by the game's
//! multiplayer screen in the player's own order (`docs/research/0008`). ash
//! reads it, and changes it only while the game is closed, keeping every byte
//! it does not change: see *changing the list* below, and ADR-0019.
//!
//! The status is the game's own server-list ping: a handshake naming the
//! instance's protocol, then a status request, answered with JSON. ash sends
//! exactly what the game sends when its multiplayer screen opens, so a server
//! sees nothing it would not see from the game.

use std::io;
use std::path::Path;

use serde::Serialize;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

const SERVERS_FILE: &str = "servers.dat";

/// The port a Minecraft server listens on unless told otherwise.
pub(crate) const DEFAULT_PORT: u16 = 25565;

/// Larger than any status answer: the JSON is capped at 32767 characters by
/// the protocol, favicon included.
const MAX_PACKET: usize = 256 * 1024;

/// A server in an instance's list.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct ServerEntry {
    pub name: String,
    /// As the player typed it: `mc.hypixel.net`, `localhost:25570`.
    pub address: String,
    /// The server's icon as the game saved it: a base64 PNG.
    pub icon: Option<String>,
    /// When the player last joined it, from ash's own record. `None` for a
    /// server they have not joined since ash began keeping one.
    pub last_joined_ms: Option<u64>,
}

/// What a server said when asked.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum ServerStatus {
    Online {
        players_online: i64,
        players_max: i64,
        /// The server's own name for its version, such as `Requires MC 1.8 / 1.21`.
        version: String,
        /// The message of the day, as plain text.
        motd: String,
        /// The icon the server sent, as a base64 PNG.
        icon: Option<String>,
    },
    /// Nothing answered in time, or what answered was not a Minecraft server.
    Offline,
}

/// The protocol a version target speaks, as its handshake states it.
///
/// Known for the first-class targets. For anything else ash says -1, which by
/// convention asks for a status without claiming a version - every server
/// still answers it.
pub(crate) fn protocol_for(version_id: &str) -> i32 {
    match version_id {
        "1.8.9" => 47,
        // From the game's own version.json.
        "1.21.11" => 774,
        _ => -1,
    }
}

// ---- servers.dat ------------------------------------------------------------

/// Why a list that exists could not be read, by kind and place only: the
/// file is the player's, and the parser's view of it stays out of the log.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct Unreadable {
    pub(crate) at: usize,
}

/// The servers the game shows in its multiplayer screen, in its order.
///
/// Empty when there is no file, which is a game that has never opened the
/// multiplayer screen. Hidden entries are left out, as the game leaves them
/// out of that screen.
pub(crate) fn read(game_directory: &Path) -> Result<Vec<ServerEntry>, Unreadable> {
    let Ok(raw) = std::fs::read(game_directory.join(SERVERS_FILE)) else {
        return Ok(Vec::new());
    };
    let mut nbt = Nbt { raw: &raw, at: 0 };
    nbt.servers().map_err(|_| Unreadable { at: nbt.at })
}

/// A reader for the NBT the game writes: uncompressed, big-endian, a root
/// compound. Only `servers` is kept; every other tag is read past.
struct Nbt<'a> {
    raw: &'a [u8],
    at: usize,
}

const TAG_END: u8 = 0;
const TAG_BYTE: u8 = 1;
const TAG_STRING: u8 = 8;
const TAG_LIST: u8 = 9;
const TAG_COMPOUND: u8 = 10;

/// Nested deeper than this is not a server list. Stops a crafted file
/// recursing until the stack runs out.
const MAX_DEPTH: usize = 32;

impl Nbt<'_> {
    fn bytes(&mut self, n: usize) -> Result<&[u8], ()> {
        let end = self.at.checked_add(n).filter(|&end| end <= self.raw.len()).ok_or(())?;
        let bytes = &self.raw[self.at..end];
        self.at = end;
        Ok(bytes)
    }

    fn u8(&mut self) -> Result<u8, ()> {
        Ok(self.bytes(1)?[0])
    }

    fn u16(&mut self) -> Result<u16, ()> {
        let b = self.bytes(2)?;
        Ok(u16::from_be_bytes([b[0], b[1]]))
    }

    fn i32(&mut self) -> Result<i32, ()> {
        let b = self.bytes(4)?;
        Ok(i32::from_be_bytes([b[0], b[1], b[2], b[3]]))
    }

    /// Java's modified UTF-8. Lossy, so a name with a malformed character
    /// still reads rather than failing the whole list.
    fn string(&mut self) -> Result<String, ()> {
        let len = self.u16()? as usize;
        Ok(read_modified_utf8(self.bytes(len)?))
    }

    fn servers(&mut self) -> Result<Vec<ServerEntry>, ()> {
        if self.u8()? != TAG_COMPOUND {
            return Err(());
        }
        self.string()?;
        let mut servers = Vec::new();
        loop {
            let tag = self.u8()?;
            if tag == TAG_END {
                return Ok(servers);
            }
            let name = self.string()?;
            if name == "servers" && tag == TAG_LIST {
                servers = self.server_list()?;
            } else {
                self.skip(tag, 0)?;
            }
        }
    }

    fn server_list(&mut self) -> Result<Vec<ServerEntry>, ()> {
        let element = self.u8()?;
        let len = self.i32()?;
        if len <= 0 {
            return Ok(Vec::new());
        }
        if element != TAG_COMPOUND {
            return Err(());
        }
        let mut servers = Vec::new();
        for _ in 0..len {
            if let Some(server) = self.server()? {
                servers.push(server);
            }
        }
        Ok(servers)
    }

    /// One entry, or `None` for a hidden one.
    fn server(&mut self) -> Result<Option<ServerEntry>, ()> {
        let (mut name, mut address, mut icon, mut hidden) = (None, None, None, false);
        loop {
            let tag = self.u8()?;
            if tag == TAG_END {
                break;
            }
            match (tag, self.string()?.as_str()) {
                (TAG_STRING, "name") => name = Some(self.string()?),
                (TAG_STRING, "ip") => address = Some(self.string()?),
                (TAG_STRING, "icon") => icon = Some(self.string()?),
                (TAG_BYTE, "hidden") => hidden = self.u8()? != 0,
                (tag, _) => self.skip(tag, 1)?,
            }
        }
        // An entry with no address cannot be pinged or joined; the game
        // shows it as unreachable, and so it is left out here.
        let Some(address) = address.filter(|a| !a.trim().is_empty()) else {
            return Ok(None);
        };
        if hidden {
            return Ok(None);
        }
        Ok(Some(ServerEntry {
            name: name.unwrap_or_default(),
            address,
            icon: icon.filter(|i| !i.is_empty()),
            last_joined_ms: None,
        }))
    }

    /// Read past one payload of `tag`.
    fn skip(&mut self, tag: u8, depth: usize) -> Result<(), ()> {
        if depth > MAX_DEPTH {
            return Err(());
        }
        let width =
            |n: i32, size: usize| usize::try_from(n).map_err(|_| ())?.checked_mul(size).ok_or(());
        match tag {
            1 => self.bytes(1).map(drop),
            2 => self.bytes(2).map(drop),
            3 | 5 => self.bytes(4).map(drop),
            4 | 6 => self.bytes(8).map(drop),
            7 => {
                let n = self.i32()?;
                self.bytes(width(n, 1)?).map(drop)
            }
            TAG_STRING => self.string().map(drop),
            TAG_LIST => {
                let element = self.u8()?;
                let n = self.i32()?;
                for _ in 0..n.max(0) {
                    self.skip(element, depth + 1)?;
                }
                Ok(())
            }
            TAG_COMPOUND => loop {
                let inner = self.u8()?;
                if inner == TAG_END {
                    return Ok(());
                }
                self.string()?;
                self.skip(inner, depth + 1)?;
            },
            11 => {
                let n = self.i32()?;
                self.bytes(width(n, 4)?).map(drop)
            }
            12 => {
                let n = self.i32()?;
                self.bytes(width(n, 8)?).map(drop)
            }
            _ => Err(()),
        }
    }
}

// ---- changing the list --------------------------------------------------------

/// The longest name and address the game's own Add Server screen accepts.
/// Kept to, so a server ash adds can be edited in the game afterwards.
const MAX_NAME: usize = 32;
const MAX_ADDRESS: usize = 128;

/// What the game calls a server the player gave no name.
const DEFAULT_NAME: &str = "Minecraft Server";

/// One change to an instance's server list, made the way the game's own
/// multiplayer screen makes it.
///
/// Every change but adding names the server it is about twice: by its
/// position among the servers the game shows, in the game's order, and by
/// the address expected there. If the file no longer has that address there,
/// the change is refused rather than made to whatever is there now.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum Change {
    /// Put a server after the last one the game shows, where the game puts a
    /// new one.
    Add {
        name: String,
        address: String,
    },
    Edit {
        position: usize,
        expected: String,
        name: String,
        address: String,
    },
    Remove {
        position: usize,
        expected: String,
    },
    /// Move a server so that it is shown at `to`.
    Move {
        position: usize,
        expected: String,
        to: usize,
    },
}

/// Why a change was not made.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum Refused {
    /// The file is there and could not be read: it is left alone.
    Unreadable,
    /// The list is not what the change was made against.
    Changed,
    InvalidName,
    InvalidAddress,
}

/// Makes `change` to the `servers.dat` in `game_directory`, and keeps
/// everything else in the file exactly as it was: every other entry byte for
/// byte, hidden ones included, and every tag ash does not know - an icon, a
/// resource-pack choice, whatever a later version adds. Within an edited
/// entry only the name and the address change.
///
/// Saved as the game saves it: the old file kept as `servers.dat_old`, and
/// the new one written beside it and moved into place, so a crash halfway
/// leaves one or the other whole.
pub(crate) fn change(game_directory: &Path, change: Change) -> Result<(), ChangeError> {
    let path = game_directory.join(SERVERS_FILE);
    let raw = match std::fs::read(&path) {
        Ok(raw) => Some(raw),
        Err(e) if e.kind() == io::ErrorKind::NotFound => None,
        Err(e) => return Err(ChangeError::Io(e)),
    };
    let updated = match &raw {
        Some(raw) => {
            let layout = Layout::of(raw).map_err(|_| ChangeError::Refused(Refused::Unreadable))?;
            layout.apply(raw, change)?
        }
        None => match change {
            Change::Add { name, address } => fresh_file(&entry_bytes(&name, &address)?),
            // No file is a list with nothing in it, so nothing is where the
            // change expected it.
            _ => return Err(ChangeError::Refused(Refused::Changed)),
        },
    };
    std::fs::create_dir_all(game_directory).map_err(ChangeError::Io)?;
    if raw.is_some() {
        std::fs::copy(&path, game_directory.join(format!("{SERVERS_FILE}_old")))
            .map_err(ChangeError::Io)?;
    }
    let temporary = game_directory.join(format!("{SERVERS_FILE}.ash-tmp"));
    std::fs::write(&temporary, &updated).map_err(ChangeError::Io)?;
    std::fs::rename(&temporary, &path).map_err(ChangeError::Io)
}

#[derive(Debug)]
pub(crate) enum ChangeError {
    Refused(Refused),
    Io(io::Error),
}

impl From<Refused> for ChangeError {
    fn from(refused: Refused) -> Self {
        ChangeError::Refused(refused)
    }
}

/// The name and address as the game will be given them, or why not. An
/// empty name becomes the game's own default, as in its Add Server screen.
pub(crate) fn tidy(name: &str, address: &str) -> Result<(String, String), Refused> {
    let name = name.trim();
    let name = if name.is_empty() { DEFAULT_NAME } else { name };
    // Counted as the game counts a text field: in UTF-16 units.
    if name.encode_utf16().count() > MAX_NAME {
        return Err(Refused::InvalidName);
    }
    let address = address.trim();
    if address.is_empty()
        || address.encode_utf16().count() > MAX_ADDRESS
        || address.chars().any(char::is_whitespace)
        || Address::parse(address).is_none()
    {
        return Err(Refused::InvalidAddress);
    }
    Ok((name.to_owned(), address.to_owned()))
}

/// Where things are in a `servers.dat`, by byte.
struct Layout {
    /// The root compound's closing `TAG_END`.
    root_end: usize,
    list: Option<ListAt>,
}

struct ListAt {
    /// The list's element type byte, then its four-byte length.
    element_at: usize,
    entries: Vec<EntryAt>,
    /// Just past the last entry: where the list ends.
    end: usize,
}

struct EntryAt {
    /// The entry compound's payload, its closing `TAG_END` included.
    start: usize,
    end: usize,
    /// As the game would show it: `None` for an entry it does not show.
    shown_address: Option<String>,
}

impl Layout {
    fn of(raw: &[u8]) -> Result<Self, ()> {
        let mut nbt = Nbt { raw, at: 0 };
        if nbt.u8()? != TAG_COMPOUND {
            return Err(());
        }
        nbt.string()?;
        let mut list = None;
        loop {
            let tag_at = nbt.at;
            let tag = nbt.u8()?;
            if tag == TAG_END {
                return Ok(Layout { root_end: tag_at, list });
            }
            let name = nbt.string()?;
            if name == "servers" && tag == TAG_LIST && list.is_none() {
                list = Some(nbt.list_at()?);
            } else {
                nbt.skip(tag, 0)?;
            }
        }
    }

    fn apply(&self, raw: &[u8], change: Change) -> Result<Vec<u8>, ChangeError> {
        let Some(list) = &self.list else {
            return match change {
                Change::Add { name, address } => {
                    let entry = entry_bytes(&name, &address)?;
                    let mut out = raw[..self.root_end].to_vec();
                    out.extend(list_tag(&[entry]));
                    out.extend_from_slice(&raw[self.root_end..]);
                    Ok(out)
                }
                _ => Err(Refused::Changed.into()),
            };
        };
        let mut entries: Vec<Vec<u8>> =
            list.entries.iter().map(|e| raw[e.start..e.end].to_vec()).collect();
        // Where each server the game shows is, among all the entries.
        let shown: Vec<usize> =
            (0..list.entries.len()).filter(|&i| list.entries[i].shown_address.is_some()).collect();
        let at = |position: usize, expected: &str| -> Result<usize, Refused> {
            let index = *shown.get(position).ok_or(Refused::Changed)?;
            match &list.entries[index].shown_address {
                Some(address) if address == expected => Ok(index),
                _ => Err(Refused::Changed),
            }
        };
        match change {
            Change::Add { name, address } => {
                let after_last_shown = shown.last().map_or(entries.len(), |&i| i + 1);
                entries.insert(after_last_shown, entry_bytes(&name, &address)?);
            }
            Change::Edit { position, expected, name, address } => {
                let index = at(position, &expected)?;
                let (name, address) = tidy(&name, &address)?;
                entries[index] =
                    edited(&entries[index], &name, &address).map_err(|_| Refused::Unreadable)?;
            }
            Change::Remove { position, expected } => {
                let index = at(position, &expected)?;
                entries.remove(index);
            }
            Change::Move { position, expected, to } => {
                let index = at(position, &expected)?;
                if to >= shown.len() {
                    return Err(Refused::Changed.into());
                }
                let moving = entries.remove(index);
                let rest: Vec<usize> = shown.iter().copied().filter(|&i| i != index).collect();
                // Before the server now shown at `to`, or after the last shown.
                let target = match rest.get(to) {
                    Some(&i) => i,
                    None => rest.last().map_or(entries.len(), |&i| i + 1),
                };
                // Indices past the one taken out have moved back by one.
                let target = if target > index { target - 1 } else { target };
                entries.insert(target, moving);
            }
        }
        let mut out = raw[..list.element_at].to_vec();
        out.push(TAG_COMPOUND);
        out.extend_from_slice(&(entries.len() as i32).to_be_bytes());
        for entry in &entries {
            out.extend_from_slice(entry);
        }
        out.extend_from_slice(&raw[list.end..]);
        Ok(out)
    }
}

impl Nbt<'_> {
    /// The `servers` list, just after its name, by where each entry is.
    fn list_at(&mut self) -> Result<ListAt, ()> {
        let element_at = self.at;
        let element = self.u8()?;
        let len = self.i32()?;
        let mut entries = Vec::new();
        if len > 0 {
            if element != TAG_COMPOUND {
                return Err(());
            }
            for _ in 0..len {
                let start = self.at;
                let shown = self.server()?;
                entries.push(EntryAt {
                    start,
                    end: self.at,
                    shown_address: shown.map(|s| s.address),
                });
            }
        }
        Ok(ListAt { element_at, entries, end: self.at })
    }
}

/// A new entry's compound payload: its name and address, as the game's own
/// entry serialiser writes them for a server it has never pinged.
fn entry_bytes(name: &str, address: &str) -> Result<Vec<u8>, Refused> {
    let (name, address) = tidy(name, address)?;
    let mut out = Vec::new();
    string_tag(&mut out, "name", &name);
    string_tag(&mut out, "ip", &address);
    out.push(TAG_END);
    Ok(out)
}

/// An entry's payload with its name and address replaced, and every other
/// tag in it kept byte for byte, in its place.
fn edited(entry: &[u8], name: &str, address: &str) -> Result<Vec<u8>, ()> {
    let mut nbt = Nbt { raw: entry, at: 0 };
    let mut out = Vec::new();
    let (mut named, mut addressed) = (false, false);
    loop {
        let start = nbt.at;
        let tag = nbt.u8()?;
        if tag == TAG_END {
            break;
        }
        let key = nbt.string()?;
        nbt.skip(tag, 1)?;
        match (tag, key.as_str()) {
            (TAG_STRING, "name") if !named => {
                string_tag(&mut out, "name", name);
                named = true;
            }
            (TAG_STRING, "ip") if !addressed => {
                string_tag(&mut out, "ip", address);
                addressed = true;
            }
            _ => out.extend_from_slice(&entry[start..nbt.at]),
        }
    }
    if !named {
        string_tag(&mut out, "name", name);
    }
    if !addressed {
        string_tag(&mut out, "ip", address);
    }
    out.push(TAG_END);
    Ok(out)
}

/// A whole `servers.dat` holding one entry, as the game writes its first.
fn fresh_file(entry: &[u8]) -> Vec<u8> {
    let mut out = vec![TAG_COMPOUND];
    write_modified_utf8(&mut out, "");
    out.extend(list_tag(&[entry.to_vec()]));
    out.push(TAG_END);
    out
}

fn list_tag(entries: &[Vec<u8>]) -> Vec<u8> {
    let mut out = vec![TAG_LIST];
    write_modified_utf8(&mut out, "servers");
    out.push(TAG_COMPOUND);
    out.extend_from_slice(&(entries.len() as i32).to_be_bytes());
    for entry in entries {
        out.extend_from_slice(entry);
    }
    out
}

fn string_tag(out: &mut Vec<u8>, key: &str, value: &str) {
    out.push(TAG_STRING);
    write_modified_utf8(out, key);
    write_modified_utf8(out, value);
}

/// Java's modified UTF-8 read back: its one-, two- and three-byte forms give
/// UTF-16 units, and a character past the basic plane is the pair of halves
/// it was written as. Read as plain UTF-8, such a character came out as
/// six replacement marks: a server named with an emoji in the game showed
/// garbled in the launcher. Anything malformed becomes one replacement mark.
fn read_modified_utf8(bytes: &[u8]) -> String {
    let mut units = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        let continuation =
            |at: usize| bytes.get(at).filter(|&&c| c & 0xC0 == 0x80).map(|&c| u16::from(c & 0x3F));
        if b & 0x80 == 0 {
            units.push(u16::from(b));
            i += 1;
        } else if b & 0xE0 == 0xC0 {
            match continuation(i + 1) {
                Some(low) => {
                    units.push((u16::from(b & 0x1F) << 6) | low);
                    i += 2;
                }
                None => {
                    units.push(0xFFFD);
                    i += 1;
                }
            }
        } else if b & 0xF0 == 0xE0 {
            match (continuation(i + 1), continuation(i + 2)) {
                (Some(mid), Some(low)) => {
                    units.push((u16::from(b & 0x0F) << 12) | (mid << 6) | low);
                    i += 3;
                }
                _ => {
                    units.push(0xFFFD);
                    i += 1;
                }
            }
        } else {
            units.push(0xFFFD);
            i += 1;
        }
    }
    String::from_utf16_lossy(&units)
}

/// Java's modified UTF-8, as `DataOutput.writeUTF` writes it and the game
/// reads it: a two-byte length, then UTF-8 but for the null character, which
/// takes two bytes, and characters past the basic plane, which go as their
/// two UTF-16 halves of three bytes each. Names and addresses are capped far
/// below the length's 65535 bytes.
fn write_modified_utf8(out: &mut Vec<u8>, text: &str) {
    let mut bytes = Vec::new();
    for unit in text.encode_utf16() {
        match unit {
            0x0001..=0x007F => bytes.push(unit as u8),
            0x0000 | 0x0080..=0x07FF => {
                bytes.push(0xC0 | (unit >> 6) as u8);
                bytes.push(0x80 | (unit & 0x3F) as u8);
            }
            _ => {
                bytes.push(0xE0 | (unit >> 12) as u8);
                bytes.push(0x80 | ((unit >> 6) & 0x3F) as u8);
                bytes.push(0x80 | (unit & 0x3F) as u8);
            }
        }
    }
    out.extend_from_slice(&(bytes.len() as u16).to_be_bytes());
    out.extend_from_slice(&bytes);
}

// ---- addresses --------------------------------------------------------------

/// A server address as the game reads one: `host`, `host:port`, `[v6]:port`,
/// or a bare IPv6 address.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Address {
    pub(crate) host: String,
    pub(crate) port: u16,
    /// Whether the player gave a port. Only an address without one is
    /// looked up for an SRV redirect.
    pub(crate) port_given: bool,
}

impl Address {
    pub(crate) fn parse(address: &str) -> Option<Self> {
        let address = address.trim();
        let (host, port) = if let Some(rest) = address.strip_prefix('[') {
            let (host, after) = rest.split_once(']')?;
            match after {
                "" => (host, None),
                _ => (host, Some(after.strip_prefix(':')?)),
            }
        } else if address.matches(':').count() == 1 {
            let (host, port) = address.split_once(':')?;
            (host, Some(port))
        } else {
            // No colon, or several: a bare IPv6 address has no port.
            (address, None)
        };
        if host.is_empty() {
            return None;
        }
        let port_given = port.is_some();
        let port = match port {
            Some(port) => port.parse().ok()?,
            None => DEFAULT_PORT,
        };
        Some(Self { host: host.to_owned(), port, port_given })
    }
}

// ---- the exchange -------------------------------------------------------------

/// The handshake a client opens with, as a server reads it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Handshake {
    pub protocol: i32,
    pub host: String,
    pub port: u16,
    /// 1 for a status request.
    pub next_state: i32,
}

impl Handshake {
    fn encode(&self) -> Vec<u8> {
        let mut packet = Vec::new();
        write_varint(&mut packet, 0x00);
        write_varint(&mut packet, self.protocol);
        write_string(&mut packet, &self.host);
        packet.extend_from_slice(&self.port.to_be_bytes());
        write_varint(&mut packet, self.next_state);
        packet
    }

    pub(crate) fn decode(packet: &[u8]) -> Option<Self> {
        let mut at = 0;
        if read_varint_from(packet, &mut at)? != 0x00 {
            return None;
        }
        let protocol = read_varint_from(packet, &mut at)?;
        let host = read_string_from(packet, &mut at)?;
        let port = u16::from_be_bytes([*packet.get(at)?, *packet.get(at + 1)?]);
        at += 2;
        let next_state = read_varint_from(packet, &mut at)?;
        Some(Self { protocol, host, port, next_state })
    }
}

/// A status response packet carrying `json`, as a server sends one.
pub(crate) fn status_response(json: &str) -> Vec<u8> {
    let mut packet = Vec::new();
    write_varint(&mut packet, 0x00);
    write_string(&mut packet, json);
    packet
}

/// Ask for a status over an open connection.
///
/// `host` and `port` are what the handshake names: the server ash actually
/// connected to, which is what the game names too.
pub(crate) async fn exchange<S: AsyncRead + AsyncWrite + Unpin + ?Sized>(
    stream: &mut S,
    protocol: i32,
    host: &str,
    port: u16,
) -> io::Result<ServerStatus> {
    let handshake = Handshake { protocol, host: host.to_owned(), port, next_state: 1 };
    write_packet(stream, &handshake.encode()).await?;
    write_packet(stream, &[0x00]).await?;

    let response = read_packet(stream).await?;
    let mut at = 0;
    let malformed = || io::Error::from(io::ErrorKind::InvalidData);
    if read_varint_from(&response, &mut at).ok_or_else(malformed)? != 0x00 {
        return Err(malformed());
    }
    let json = read_string_from(&response, &mut at).ok_or_else(malformed)?;
    parse_status(&json).ok_or_else(malformed)
}

fn parse_status(json: &str) -> Option<ServerStatus> {
    let value: serde_json::Value = serde_json::from_str(json).ok()?;
    let players = value.get("players");
    let count = |key: &str| players.and_then(|p| p.get(key)).and_then(|n| n.as_i64()).unwrap_or(0);
    let icon = value
        .get("favicon")
        .and_then(|f| f.as_str())
        .and_then(|f| f.strip_prefix("data:image/png;base64,"))
        .map(|f| f.replace(['\n', '\r'], ""));
    Some(ServerStatus::Online {
        players_online: count("online"),
        players_max: count("max"),
        version: value
            .pointer("/version/name")
            .and_then(|v| v.as_str())
            .map(plain)
            .unwrap_or_default(),
        motd: value.get("description").map(component_text).map(|t| plain(&t)).unwrap_or_default(),
        icon,
    })
}

/// A chat component's text: a string, or an object's `text` followed by its
/// `extra` children.
fn component_text(value: &serde_json::Value) -> String {
    match value {
        serde_json::Value::String(text) => text.clone(),
        serde_json::Value::Array(parts) => parts.iter().map(component_text).collect(),
        serde_json::Value::Object(object) => {
            let mut text =
                object.get("text").and_then(|t| t.as_str()).unwrap_or_default().to_owned();
            if let Some(extra) = object.get("extra") {
                text.push_str(&component_text(extra));
            }
            text
        }
        _ => String::new(),
    }
}

/// Text without the game's `§` formatting codes, and each line trimmed.
fn plain(text: &str) -> String {
    let mut out = String::new();
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out.lines().map(str::trim).filter(|l| !l.is_empty()).collect::<Vec<_>>().join("\n")
}

// ---- packets ----------------------------------------------------------------

/// One packet: its length as a VarInt, then its bytes.
pub(crate) async fn write_packet<W: AsyncWrite + Unpin + ?Sized>(
    stream: &mut W,
    packet: &[u8],
) -> io::Result<()> {
    let mut framed = Vec::with_capacity(packet.len() + 5);
    write_varint(&mut framed, packet.len() as i32);
    framed.extend_from_slice(packet);
    stream.write_all(&framed).await?;
    stream.flush().await
}

pub(crate) async fn read_packet<R: AsyncRead + Unpin + ?Sized>(
    stream: &mut R,
) -> io::Result<Vec<u8>> {
    let mut len: u32 = 0;
    for shift in (0..35).step_by(7) {
        let byte = stream.read_u8().await?;
        len |= u32::from(byte & 0x7f) << shift;
        if byte & 0x80 == 0 {
            let len = len as usize;
            if len == 0 || len > MAX_PACKET {
                return Err(io::ErrorKind::InvalidData.into());
            }
            let mut packet = vec![0; len];
            stream.read_exact(&mut packet).await?;
            return Ok(packet);
        }
    }
    Err(io::ErrorKind::InvalidData.into())
}

fn write_varint(out: &mut Vec<u8>, value: i32) {
    let mut value = value as u32;
    loop {
        if value & !0x7f == 0 {
            out.push(value as u8);
            return;
        }
        out.push((value & 0x7f | 0x80) as u8);
        value >>= 7;
    }
}

fn read_varint_from(bytes: &[u8], at: &mut usize) -> Option<i32> {
    let mut value: u32 = 0;
    for shift in (0..35).step_by(7) {
        let byte = *bytes.get(*at)?;
        *at += 1;
        value |= u32::from(byte & 0x7f) << shift;
        if byte & 0x80 == 0 {
            return Some(value as i32);
        }
    }
    None
}

fn write_string(out: &mut Vec<u8>, text: &str) {
    write_varint(out, text.len() as i32);
    out.extend_from_slice(text.as_bytes());
}

fn read_string_from(bytes: &[u8], at: &mut usize) -> Option<String> {
    let len = usize::try_from(read_varint_from(bytes, at)?).ok()?;
    let end = at.checked_add(len).filter(|&end| end <= bytes.len())?;
    let text = std::str::from_utf8(&bytes[*at..end]).ok()?.to_owned();
    *at = end;
    Some(text)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn addresses_read_the_way_the_game_reads_them() {
        let parse = |a: &str| Address::parse(a).map(|a| (a.host, a.port, a.port_given));

        assert_eq!(parse("mc.hypixel.net"), Some(("mc.hypixel.net".into(), 25565, false)));
        assert_eq!(parse(" localhost:25570 "), Some(("localhost".into(), 25570, true)));
        assert_eq!(parse("[::1]:25566"), Some(("::1".into(), 25566, true)));
        assert_eq!(parse("[::1]"), Some(("::1".into(), 25565, false)));
        assert_eq!(parse("fe80::1"), Some(("fe80::1".into(), 25565, false)));
        assert_eq!(parse("host:notaport"), None);
        assert_eq!(parse(":25565"), None);
        assert_eq!(parse(""), None);
    }

    #[test]
    fn a_handshake_survives_the_trip() {
        let handshake =
            Handshake { protocol: 774, host: "mc.example".into(), port: 25565, next_state: 1 };

        assert_eq!(Handshake::decode(&handshake.encode()), Some(handshake));
    }

    #[test]
    fn varints_match_the_protocols_examples() {
        // From the protocol's own table of examples.
        for (value, bytes) in [
            (0, vec![0x00]),
            (127, vec![0x7f]),
            (128, vec![0x80, 0x01]),
            (25565, vec![0xdd, 0xc7, 0x01]),
            (-1, vec![0xff, 0xff, 0xff, 0xff, 0x0f]),
        ] {
            let mut out = Vec::new();
            write_varint(&mut out, value);
            assert_eq!(out, bytes, "{value}");
            assert_eq!(read_varint_from(&bytes, &mut 0), Some(value));
        }
    }

    #[test]
    fn a_motd_is_read_from_a_string_or_a_component_without_its_colours() {
        let status = |description: &str| {
            parse_status(&format!(
                r#"{{"players":{{"online":3,"max":20}},"description":{description}}}"#
            ))
        };
        let motd = |s: Option<ServerStatus>| match s {
            Some(ServerStatus::Online { motd, .. }) => motd,
            other => panic!("{other:?}"),
        };

        assert_eq!(motd(status(r#""§aHello §lworld""#)), "Hello world");
        assert_eq!(
            motd(status(r#"{"text":"Hypixel ","extra":[{"text":"Network","color":"red"}]}"#)),
            "Hypixel Network"
        );
    }

    #[test]
    fn a_list_nested_past_any_real_one_is_refused_rather_than_followed() {
        // A root compound whose one tag is a list of lists of lists ...
        let mut raw = vec![TAG_COMPOUND, 0, 0, TAG_LIST, 0, 1, b'x'];
        for _ in 0..100 {
            raw.extend_from_slice(&[TAG_LIST, 0, 0, 0, 1]);
        }
        let mut nbt = Nbt { raw: &raw, at: 0 };

        assert!(nbt.servers().is_err());
    }
}
