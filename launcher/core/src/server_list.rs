//! An instance's servers: the game's own list, and each server's status.
//!
//! The list is `servers.dat` in the game directory, written by the game's
//! multiplayer screen in the player's own order. It is the player's file and
//! the game's, so ash only reads it. See `docs/research/0008`.
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

    /// Java's modified UTF-8. Lossy, so a name with a character Rust would
    /// spell differently still reads rather than failing the whole list.
    fn string(&mut self) -> Result<String, ()> {
        let len = self.u16()? as usize;
        Ok(String::from_utf8_lossy(self.bytes(len)?).into_owned())
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
