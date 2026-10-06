//! The server port: how ash reaches a game server to ask for its status.
//!
//! Like the process port, this is the one place a real effect happens - a
//! DNS query and a TCP connection to somebody else's server - so it goes
//! behind a trait. The protocol itself is ash-core's, in `server_list`, and
//! runs over whatever stream the port hands back: a socket in the launcher, an
//! in-memory pipe to a scripted server in the tests.

use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

use async_trait::async_trait;
use tokio::io::{AsyncRead, AsyncWrite, DuplexStream};

use crate::server_list::{read_packet, status_response, write_packet, Handshake};

/// A connection to a server, in both directions.
pub trait ServerStream: AsyncRead + AsyncWrite + Send + Unpin {}

impl<T: AsyncRead + AsyncWrite + Send + Unpin> ServerStream for T {}

/// Reaching a game server.
#[async_trait]
pub trait ServerPort: Send + Sync {
    /// Where the `_minecraft._tcp` SRV record for `host` points, if it has
    /// one: the record's target and port.
    async fn srv(&self, host: &str) -> Option<(String, u16)>;

    async fn connect(&self, host: &str, port: u16) -> std::io::Result<Box<dyn ServerStream>>;
}

// ---- the real one ----------------------------------------------------------

/// The system's DNS and a real TCP connection.
#[derive(Default)]
pub struct OsServerPort {
    /// Built on first use, inside the runtime that will drive it. `None`
    /// when the system's DNS settings cannot be read, which costs SRV
    /// redirects and nothing else.
    resolver: OnceLock<Option<hickory_resolver::TokioResolver>>,
}

impl OsServerPort {
    pub fn new() -> Self {
        Self::default()
    }
}

#[async_trait]
impl ServerPort for OsServerPort {
    async fn srv(&self, host: &str) -> Option<(String, u16)> {
        use hickory_resolver::proto::rr::{RData, RecordType};

        let resolver = self
            .resolver
            .get_or_init(|| hickory_resolver::TokioResolver::builder_tokio().ok()?.build().ok())
            .as_ref()?;
        let lookup =
            resolver.lookup(format!("_minecraft._tcp.{host}."), RecordType::SRV).await.ok()?;
        // The first record, as the game takes it.
        lookup.answers().iter().find_map(|record| match &record.data {
            RData::SRV(srv) => {
                let target = srv.target.to_utf8();
                let target = target.trim_end_matches('.');
                // "." means the service is decidedly not available there.
                (!target.is_empty()).then(|| (target.to_owned(), srv.port))
            }
            _ => None,
        })
    }

    async fn connect(&self, host: &str, port: u16) -> std::io::Result<Box<dyn ServerStream>> {
        let stream = tokio::net::TcpStream::connect((host, port)).await?;
        // A status exchange is two small packets each way, so waiting to
        // fill a segment only adds latency.
        let _ = stream.set_nodelay(true);
        Ok(Box::new(stream))
    }
}

// ---- the fake --------------------------------------------------------------

/// How a scripted server behaves.
#[derive(Debug, Clone)]
pub enum FakeServer {
    /// Answers a status request with this JSON.
    Online { status: String },
    /// Accepts the connection and never says anything.
    Silent,
}

/// Scripted servers, reached through in-memory pipes. Any address it has no
/// server for refuses the connection, as a real host with nothing listening
/// does.
#[derive(Default)]
pub struct FakeServerPort {
    servers: Mutex<HashMap<(String, u16), FakeServer>>,
    srv: Mutex<HashMap<String, (String, u16)>>,
    connections: Mutex<Vec<(String, u16)>>,
    handshakes: Arc<Mutex<Vec<Handshake>>>,
    /// Silent servers' ends of their pipes, kept so the connection stays
    /// open rather than closing the moment it is made.
    held: Mutex<Vec<DuplexStream>>,
}

impl FakeServerPort {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    pub fn serve(self: &Arc<Self>, host: &str, port: u16, server: FakeServer) -> Arc<Self> {
        self.servers.lock().unwrap().insert((host.to_owned(), port), server);
        Arc::clone(self)
    }

    /// Give `host` an SRV record pointing at `target:port`.
    pub fn redirect(self: &Arc<Self>, host: &str, target: &str, port: u16) -> Arc<Self> {
        self.srv.lock().unwrap().insert(host.to_owned(), (target.to_owned(), port));
        Arc::clone(self)
    }

    /// Every connection asked for, in order, refused ones included.
    pub fn connections(&self) -> Vec<(String, u16)> {
        self.connections.lock().unwrap().clone()
    }

    /// Every handshake an online server received, in order.
    pub fn handshakes(&self) -> Vec<Handshake> {
        self.handshakes.lock().unwrap().clone()
    }
}

#[async_trait]
impl ServerPort for FakeServerPort {
    async fn srv(&self, host: &str) -> Option<(String, u16)> {
        self.srv.lock().unwrap().get(host).cloned()
    }

    async fn connect(&self, host: &str, port: u16) -> std::io::Result<Box<dyn ServerStream>> {
        self.connections.lock().unwrap().push((host.to_owned(), port));
        let server = self.servers.lock().unwrap().get(&(host.to_owned(), port)).cloned();
        let (ours, mut theirs) = tokio::io::duplex(64 * 1024);
        match server {
            None => return Err(std::io::ErrorKind::ConnectionRefused.into()),
            Some(FakeServer::Silent) => self.held.lock().unwrap().push(theirs),
            Some(FakeServer::Online { status }) => {
                let handshakes = Arc::clone(&self.handshakes);
                tokio::spawn(async move {
                    let Ok(handshake) = read_packet(&mut theirs).await else { return };
                    if let Some(handshake) = Handshake::decode(&handshake) {
                        handshakes.lock().unwrap().push(handshake);
                    }
                    // The status request: an empty packet 0x00.
                    if read_packet(&mut theirs).await.is_err() {
                        return;
                    }
                    let _ = write_packet(&mut theirs, &status_response(&status)).await;
                });
            }
        }
        Ok(Box::new(ours))
    }
}
