use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use serde::Serialize;

use crate::error::AshError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Method {
    Get,
    Post,
}

#[derive(Debug, Clone)]
pub struct HttpRequest {
    pub method: Method,
    pub url: String,
    pub headers: Vec<(String, String)>,
    pub body: Option<Vec<u8>>,
}

impl HttpRequest {
    pub fn get(url: impl Into<String>) -> Self {
        Self { method: Method::Get, url: url.into(), headers: Vec::new(), body: None }
    }

    pub fn post_json(url: impl Into<String>, body: &impl Serialize) -> Result<Self, AshError> {
        let encoded = serde_json::to_vec(body)
            .map_err(|e| AshError::Malformed { url: String::new(), detail: e.to_string() })?;
        Ok(Self {
            method: Method::Post,
            url: url.into(),
            headers: vec![
                ("Content-Type".into(), "application/json".into()),
                ("Accept".into(), "application/json".into()),
            ],
            body: Some(encoded),
        })
    }

    /// `application/x-www-form-urlencoded`, which is what the Microsoft
    /// identity platform token endpoints take.
    pub fn post_form(url: impl Into<String>, pairs: &[(&str, &str)]) -> Self {
        let body = pairs
            .iter()
            .map(|(k, v)| format!("{}={}", percent_encode(k), percent_encode(v)))
            .collect::<Vec<_>>()
            .join("&");
        Self {
            method: Method::Post,
            url: url.into(),
            headers: vec![(
                "Content-Type".into(),
                "application/x-www-form-urlencoded".into(),
            )],
            body: Some(body.into_bytes()),
        }
    }

    /// Resume from a byte offset. Answered with 206 and the remaining bytes,
    /// or 200 and the whole file if the server ignores it.
    pub fn range_from(mut self, offset: u64) -> Self {
        self.headers.push(("Range".into(), format!("bytes={offset}-")));
        self
    }

    pub fn bearer(mut self, token: &str) -> Self {
        self.headers.push(("Authorization".into(), format!("Bearer {token}")));
        self
    }
}

/// Percent-encode for form bodies. Scopes carry spaces and `grant_type`
/// values carry colons and slashes, so this cannot be skipped.
fn percent_encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(byte as char)
            }
            b' ' => out.push('+'),
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}

#[derive(Debug, Clone)]
pub struct HttpResponse {
    pub status: u16,
    pub body: Vec<u8>,
}

impl HttpResponse {
    pub fn ok(body: impl Into<Vec<u8>>) -> Self {
        Self { status: 200, body: body.into() }
    }

    pub fn json(status: u16, body: impl Into<Vec<u8>>) -> Self {
        Self { status, body: body.into() }
    }

    pub fn status(status: u16) -> Self {
        Self { status, body: Vec::new() }
    }

    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }
}

/// Every byte ash sends or receives over the network goes through here.
///
/// This is the outbound seam. Tests swap in [`FakeHttp`]; nothing else in
/// ash-core knows whether it is talking to Mojang or to a fixture.
#[async_trait]
pub trait HttpPort: Send + Sync {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, AshError>;

    async fn get(&self, url: &str) -> Result<HttpResponse, AshError> {
        self.send(HttpRequest::get(url)).await
    }
}

/// The real one.
pub struct ReqwestHttp {
    client: reqwest::Client,
}

impl ReqwestHttp {
    pub fn new() -> Self {
        let client = reqwest::Client::builder()
            .user_agent(concat!("ash/", env!("CARGO_PKG_VERSION")))
            .build()
            .expect("reqwest client builds with static configuration");
        Self { client }
    }
}

impl Default for ReqwestHttp {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl HttpPort for ReqwestHttp {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, AshError> {
        let mut builder = match request.method {
            Method::Get => self.client.get(&request.url),
            Method::Post => self.client.post(&request.url),
        };
        for (name, value) in &request.headers {
            builder = builder.header(name, value);
        }
        if let Some(body) = request.body {
            builder = builder.body(body);
        }

        let response = builder.send().await.map_err(|e| AshError::Transport {
            url: request.url.clone(),
            detail: e.to_string(),
        })?;
        let status = response.status().as_u16();
        let body = response
            .bytes()
            .await
            .map_err(|e| AshError::Transport { url: request.url.clone(), detail: e.to_string() })?
            .to_vec();
        Ok(HttpResponse { status, body })
    }
}

fn strip_query(url: &str) -> String {
    url.split('?').next().unwrap_or(url).to_owned()
}

/// Same endpoint, ignoring any query string.
fn same_endpoint(recorded: &str, wanted: &str) -> bool {
    recorded == wanted || strip_query(recorded) == strip_query(wanted)
}

/// The fake. Serves canned responses by URL and records what was asked for,
/// so a test can assert ash did not reach for anything it shouldn't.
///
/// This lives in the library rather than behind a feature flag on purpose:
/// the fake is part of the design, not an afterthought, and every test in the
/// workspace is expected to reach for it.
#[derive(Default)]
pub struct FakeHttp {
    routes: Mutex<HashMap<String, VecDeque<HttpResponse>>>,
    requested: Mutex<Vec<HttpRequest>>,
}

impl FakeHttp {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    /// Register a response served for every call to this URL.
    pub fn route(self: &Arc<Self>, url: impl Into<String>, response: HttpResponse) -> Arc<Self> {
        self.routes.lock().unwrap().insert(url.into(), VecDeque::from(vec![response]));
        Arc::clone(self)
    }

    /// Register responses served in order, the last one repeating.
    ///
    /// The device-code token endpoint is polled until it stops saying
    /// `authorization_pending`, so a single canned response cannot express it.
    pub fn route_sequence(
        self: &Arc<Self>,
        url: impl Into<String>,
        responses: Vec<HttpResponse>,
    ) -> Arc<Self> {
        assert!(!responses.is_empty(), "a route needs at least one response");
        self.routes.lock().unwrap().insert(url.into(), VecDeque::from(responses));
        Arc::clone(self)
    }

    /// Every URL requested, in order.
    pub fn requested(&self) -> Vec<String> {
        self.requested.lock().unwrap().iter().map(|r| r.url.clone()).collect()
    }

    /// How many times a URL was requested, ignoring any query string.
    pub fn hits(&self, url: &str) -> usize {
        self.requested.lock().unwrap().iter().filter(|r| same_endpoint(&r.url, url)).count()
    }

    /// The body sent to a URL, for asserting request shape.
    pub fn last_body(&self, url: &str) -> Option<String> {
        self.requested
            .lock()
            .unwrap()
            .iter()
            .rev()
            .find(|r| same_endpoint(&r.url, url))
            .and_then(|r| r.body.clone())
            .map(|b| String::from_utf8_lossy(&b).into_owned())
    }

    /// Headers sent to a URL, for asserting a bearer token was attached.
    pub fn last_headers(&self, url: &str) -> Vec<(String, String)> {
        self.requested
            .lock()
            .unwrap()
            .iter()
            .rev()
            .find(|r| same_endpoint(&r.url, url))
            .map(|r| r.headers.clone())
            .unwrap_or_default()
    }
}

#[async_trait]
impl HttpPort for FakeHttp {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, AshError> {
        let url = request.url.clone();
        self.requested.lock().unwrap().push(request);

        // Take the routes lock exactly once, and drop it before deciding what
        // to do - panicking while holding it would deadlock the message.
        let found = {
            let mut routes = self.routes.lock().unwrap();
            // Exact match first, then the endpoint without its query string.
            // The entitlements call carries a random `requestId`, so a test
            // cannot possibly name the whole URL up front.
            let key = if routes.contains_key(&url) {
                Some(url.clone())
            } else {
                let base = strip_query(&url);
                if routes.contains_key(&base) { Some(base) } else { None }
            };

            match key {
                Some(k) => {
                    let queue = routes.get_mut(&k).expect("key was just checked");
                    // The last response repeats, so a sequence need not predict
                    // exactly how many polls a test will make.
                    let response =
                        if queue.len() > 1 { queue.pop_front() } else { queue.front().cloned() };
                    Ok(response.expect("a route always holds at least one response"))
                }
                None => Err(routes.keys().cloned().collect::<Vec<String>>()),
            }
        };

        match found {
            Ok(response) => Ok(response),
            // An unrouted URL is a test authoring mistake, not a network
            // failure. Say so loudly rather than inventing a plausible 404.
            Err(known) => panic!("FakeHttp has no route for {url}\nrouted: {known:?}"),
        }
    }
}
