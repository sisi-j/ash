use async_trait::async_trait;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::error::AshError;

/// A response body and the status that came with it. Deliberately not
/// `reqwest::Response` - the port must not leak its implementation.
#[derive(Debug, Clone)]
pub struct HttpResponse {
    pub status: u16,
    pub body: Vec<u8>,
}

impl HttpResponse {
    pub fn ok(body: impl Into<Vec<u8>>) -> Self {
        Self { status: 200, body: body.into() }
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
    async fn get(&self, url: &str) -> Result<HttpResponse, AshError>;
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
    async fn get(&self, url: &str) -> Result<HttpResponse, AshError> {
        let response = self.client.get(url).send().await.map_err(|e| AshError::Transport {
            url: url.to_owned(),
            detail: e.to_string(),
        })?;
        let status = response.status().as_u16();
        let body = response
            .bytes()
            .await
            .map_err(|e| AshError::Transport { url: url.to_owned(), detail: e.to_string() })?
            .to_vec();
        Ok(HttpResponse { status, body })
    }
}

/// The fake. Serves canned responses by exact URL and records what was asked
/// for, so a test can assert ash did not reach for anything it shouldn't.
///
/// This lives in the library rather than behind a feature flag on purpose:
/// the fake is part of the design, not an afterthought, and every test in the
/// workspace is expected to reach for it.
#[derive(Default)]
pub struct FakeHttp {
    routes: Mutex<HashMap<String, HttpResponse>>,
    requested: Mutex<Vec<String>>,
}

impl FakeHttp {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    /// Register a canned response. Returns self so calls chain.
    pub fn route(self: &Arc<Self>, url: impl Into<String>, response: HttpResponse) -> Arc<Self> {
        self.routes.lock().unwrap().insert(url.into(), response);
        Arc::clone(self)
    }

    /// Every URL requested, in order.
    pub fn requested(&self) -> Vec<String> {
        self.requested.lock().unwrap().clone()
    }
}

#[async_trait]
impl HttpPort for FakeHttp {
    async fn get(&self, url: &str) -> Result<HttpResponse, AshError> {
        self.requested.lock().unwrap().push(url.to_owned());

        // Take the routes lock exactly once, and drop it before deciding what
        // to do - panicking while holding it would deadlock the message.
        let found = {
            let routes = self.routes.lock().unwrap();
            match routes.get(url) {
                Some(response) => Ok(response.clone()),
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
