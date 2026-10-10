# ash's backend, as its own contract describes it

These files are copies of [`sisi-j/ash-backend`'s `contract/`](https://github.com/sisi-j/ash-backend/tree/main/contract): one example per response shape. The backend's tests check every real response against the same file.

`tests/ash_account.rs` serves them from a fake backend. The `backend-contract` CI job checks that each copy is byte-for-byte what the backend has on `main`. A change on either side then fails a test somewhere, without the two repositories sharing any code (ADR-0021).

To update them, copy the backend's files over these, and change the launcher to match.
