# Third-party launcher access to the Minecraft Services API, and what the commercial terms permit

Research date: 2026-09-09. All sources retrieved on that date unless stated otherwise.

Every claim below is labelled:

- **[DOC]** — the first-party documentation says this.
- **[PRACTICE]** — observed behaviour of the live API, or shipped code in an established launcher.
- **[COMMUNITY]** — a reverse-engineered or community source is the only available evidence.

---

## Summary

**Yes, it is gated, and ash is on the wrong side of the gate.**

Since 2023-05-30, Mojang has run a manual allow-list for third-party applications that call the Minecraft: Java Edition game service APIs. Applications that already had access when the policy was announced were grandfathered; **every new application must apply through a form** at <https://aka.ms/mce-reviewappid> ([Minecraft Help, "Java Edition Game Service API Review or Application Process"](https://help.minecraft.net/hc/en-us/articles/16254801392141-Java-Edition-Game-Service-API-Review-or-Application-Process), article created 2023-05-30, body last edited 2026-03-11, record last touched 2026-09-08) **[DOC]**.

Enforcement is live and current. A fresh, correctly configured Azure app registration completes Microsoft OAuth, Xbox Live authentication and XSTS authorization successfully, then receives `HTTP 403 Invalid app registration, see https://aka.ms/AppRegInfo` from `api.minecraftservices.com` — reported by independent developers in [February 2026](https://learn.microsoft.com/en-gb/answers/questions/5768276/how-to-get-xboxlive-signin-permission-for-azure-ap) and [August 2026](https://learn.microsoft.com/en-us/answers/questions/5989335/minecraft-services-returns-http-403-invalid-app-re) **[PRACTICE]**. `aka.ms/AppRegInfo` — the link inside the error itself — resolves to that same help article (verified by following the redirect chain, 2026-09-09), which closes the loop: the API tells you you are not allow-listed, and points at the form.

**Updated 2026-09-11 — the form itself publishes what the help article does not.** See §11. Submissions are "reviewed weekly", multiple submissions do not speed it up, and applications that bypass security, auth or licence checks are stated outright as not approvable. There is still no SLA, no acknowledgement, no status page and no public record of any applicant's outcome — but "reviewed weekly" is a real cadence and supersedes the earlier reading of the wait as wholly unknowable.

**What this means for Phase 1.** The gate does not block writing code, and it does not block most of the spec. It blocks exactly one thing, and it is the thing the spec calls the definition of done:

- Everything up to and including the XSTS hop works today with an unapproved client ID **[PRACTICE]**, so the token chain can be built and debugged for real.
- The whole chain, including `login_with_xbox`, the entitlement check and the profile fetch, is testable against the recorded HTTP fixtures the spec already mandates. No approval needed.
- The spec's "Manual acceptance, not automated" clause — a real account, a real machine, both targets reaching the main menu signed in as the correct player — **cannot be satisfied without approval**. Neither can user stories 6–22, end to end, against a live account.

So: submit the form now, before writing a line of Rust. It costs nothing, it is the single longest-lead item in the project, and its duration is entirely outside the team's control. Everything else in Phase 1 (catalogue, instances, depot, runtime provisioning, launch construction, 1.8.9 legacy arguments) is unaffected and can proceed in parallel.

**The secondary question is the more dangerous one.** The paid-cosmetics plan collides head-on with a specific, current bullet in the Minecraft Usage Guidelines: a mod "cannot be used to directly or indirectly verify whether a player owns or has access to out-of-game content, products, or services that affect in-game features and functions" **[DOC]**. An ash account holding a purchased entitlement that unlocks a cape is, on the plain text, exactly that. This is a business-model risk, not a paperwork risk, and it does not have an application form. See §8.

---

## Confidence and gaps

Read this section before acting on anything below.

### Could not verify from any source

1. ~~**The contents of the application form.**~~ **CLOSED 2026-09-11.** Captured verbatim in §11 from the live form. It accepts responses, does not require sign-in, and does not auto-collect the submitter's identity. Nine questions; both "request approval" and "report a bad app" run through it, confirming the earlier inference that it is a general app-id intake rather than developer onboarding.
2. **Lead time / turnaround.** **Partially closed 2026-09-11.** The form states "Submissions are reviewed weekly, multiple submissions will not make the process go faster." So there is a review *cadence*, published on the form and nowhere else. Still unknown: how many weekly cycles a submission may sit through, whether a decision is communicated at all, and what happens on rejection. There remains no developer portal, status page or queue.
3. **Eligibility criteria.** **Partially closed 2026-09-11.** The form states three: the applicant must confirm having read the EULA and Usage Guidelines; the application name must not contain Mojang, Minecraft, Microsoft, Live, Xbox, Discord or Hypixel; and "any applications that bypass security/auth/license checks or disable safety features will not be approved." A justification is mandatory — "submissions that do not have a valid justification will not be reviewed." Still unstated: commercial vs non-commercial, open vs closed source, company vs individual.
4. **Any public record of an approval or rejection.** No GitHub issue, changelog, release note or repo doc found in any established launcher describes going through this form. Searched Prism Launcher, MultiMC, ATLauncher, HMCL, PolyMC. This is consistent with all of them predating the policy (see §6) and having had no reason to apply.
5. **Whether grandfathering still holds in 2026.** The article's wording ("Existing applications, such as launchers and websites, will continue to have access without interruption") is unchanged as of the 2026-03-11 edit, but no primary source confirms the promise is still being honoured, and no primary source describes what would revoke it.
6. **Whether Lunar Client or Badlion hold any bespoke agreement with Mojang or Microsoft.** No public evidence either way. Their own terms explicitly disclaim affiliation (§10). Their practice is not evidence of permission.
7. **Whether ash's cosmetics model is actually prohibited.** §8 sets out the text and the plain reading. It is a reading, not a ruling. Mojang states directly: "We are not able to give advice about whether a specific project does or does not comply with these guidelines. If you are unsure, you should speak to an attorney for help" ([Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)) **[DOC]**.

### Sources are thin, conflicting or out of date

8. **The Minecraft Services API has no first-party documentation at all.** Not on `learn.microsoft.com`, not on `minecraft.net`, not on `help.minecraft.net`. Microsoft documents the identity-platform half of the chain (device code, token endpoints, app registration) thoroughly. **Everything from `user.auth.xboxlive.com` onward is community-reverse-engineered or read directly off open-source launcher code.** §4 marks each hop.
9. **The two Microsoft Q&A threads are the strongest live evidence of enforcement, and neither has a Microsoft answer.** The Feb 2026 thread's accepted answer — pointing at the Xbox Developer Program / ID@Xbox — is from a **volunteer moderator and Student Ambassador, not a Microsoft employee** (the page labels them as such). It **conflicts** with the Minecraft help article, which names a completely different, Mojang-run process. Weight the help article; it owns the `aka.ms/AppRegInfo` link that the API itself returns. Treat the Xbox Developer Program answer as probably wrong for this use case.
10. **No documented rate limits for `api.minecraftservices.com`.** None published. §5 reports what is observable and what Microsoft documents for its own identity endpoints instead.
11. **The EULA and Usage Guidelines carry no revision date on the page.** The `Last-Modified` header on the Usage Guidelines is a CDN/render timestamp, not a policy revision date. The most recent dated announcement of a substantive change is 2023-08-02. So the guidelines quoted in §7–§9 are current as served on 2026-09-09, but I cannot tell you when the mod bullets were last edited.
12. **Two different endpoint variants are in live use for the same two steps** (`/launcher/login` vs `/authentication/login_with_xbox`; `/entitlements/license` vs `/entitlements/mcstore`), with no first-party statement about which is correct or supported. §4 documents both.
13. **The Game Pass entitlement path (user story 13) rests on a single community claim** that a Game Pass account which has never signed into the official launcher returns no profile. Unverified against any first-party source.

---

## 1. Is approval required, and how do we get it?

**Yes. Since 2023-05-30, and it is enforced today.**

### The policy, verbatim

From [Minecraft Help — "Java Edition Game Service API Review or Application Process"](https://help.minecraft.net/hc/en-us/articles/16254801392141-Java-Edition-Game-Service-API-Review-or-Application-Process) (article id 16254801392141; `created_at` 2023-05-30T15:53:19Z, `edited_at` 2026-03-11T18:49:31Z, `updated_at` 2026-09-08T13:28:52Z — metadata read from the help centre's own content API, 2026-09-09) **[DOC]**:

> Mojang is improving the Minecraft: Java Edition ecosystem regarding third-party applications that interact with the Java Edition game service APIs. While many of these third-party applications integrate successfully into our services, some attempt to maliciously exploit our users through methods such as phishing attempts.
>
> We're enforcing new policies on third-party apps that seek to access Java game service APIs. From now on, we will be reviewing and manually adding all future API integration requests to an allow list. Existing applications, such as launchers and websites, will continue to have access without interruption. However, new applications must request access via **this form**.
>
> We're implementing these measures to provide a more secure experience for players and developers, and we greatly appreciate your understanding. We also understand the importance of community involvement when it comes to identifying suspicious applications. If you find a suspicious application, please report it using the form linked above. If you have any additional questions or concerns, please contact us **here**.

The two links in that body are:

- "this form" → **<https://aka.ms/mce-reviewappid>**
- "here" → **`mailto:enforce@minecraft.net`**

Note the article's title changed at some point from "**New** Java Edition Game Service API Review or Application Process" to the current wording (both slugs exist in the Internet Archive's index for article id 16254801392141), which is consistent with the policy no longer being new.

### Where the form actually is

`https://aka.ms/mce-reviewappid` → (2 redirects) → `https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-ajEQ1td1ROpz00KtS8Gd5UNVpPTkVLNFVROVQxNkdRMEtXVjNQQjdXVC4u` (verified 2026-09-09) **[PRACTICE]**.

Decoding the form id gives tenant `72f988bf-86f1-41af-91ab-2d7cd011db47` — Microsoft Corporation's own Entra tenant. So this is a Microsoft-operated intake form, not a third-party one. **What it asks is unverified** (see gap 1).

The same form is used to *report* suspicious applications, per the article body. That dual purpose suggests it is a general "review an app id" intake rather than a dedicated developer onboarding flow, which is consistent with there being no published criteria or SLA.

### Proof the gate is enforced, not aspirational

The Minecraft Services API returns, on `login_with_xbox` / `launcher/login`, after every preceding hop has succeeded:

```
HTTP/1.1 403 Forbidden
Invalid app registration, see https://aka.ms/AppRegInfo
```

Two independent reports on Microsoft's own Q&A site **[PRACTICE]**:

- [2026-08-30, "Minecraft Services returns HTTP 403 'Invalid app registration' for HyperKraft third-party Java launcher"](https://learn.microsoft.com/en-us/answers/questions/5989335/minecraft-services-returns-http-403-invalid-app-re) — the developer reports Microsoft OAuth device authorization grant SUCCESS, Xbox Live SUCCESS, XSTS SUCCESS, then 403 at `/authentication/login_with_xbox`. Asks which team handles allow-listing. **Still unanswered**; the only reply, 2026-09-05, is another developer saying "Im having the same issue here."
- [2026-02-09, "How to get XboxLive.signin permission for Azure App Registration (Minecraft Launcher)"](https://learn.microsoft.com/en-gb/answers/questions/5768276/how-to-get-xboxlive-signin-permission-for-azure-ap) — a hobbyist with public client flows enabled, redirect URIs configured, multitenant + personal accounts, gets `Invalid app registration, see https://aka.ms/AppRegInfo`, 403. Accepted answer (volunteer moderator, **not** Microsoft staff) points at the Xbox Developer Program / ID@Xbox. See gap 9 — this answer conflicts with the Mojang help article and should not be relied on.

And `https://aka.ms/AppRegInfo` → `https://help.minecraft.net/hc/en-us/articles/16254801392141` (verified 2026-09-09) **[PRACTICE]**. The error message and the application process point at each other.

### What the article does *not* say

No eligibility criteria. No turnaround time. No fee. No distinction between commercial and non-commercial. No requirement to be a registered company. No statement of what gets rejected. No appeal path beyond `enforce@minecraft.net`.

---

## 2. What the Azure app registration needs

Microsoft documents this half thoroughly. The Minecraft-specific constraint (the `consumers` authority) is community-sourced but universally corroborated by shipped launcher code.

| Setting | Value | Evidence |
| --- | --- | --- |
| Supported account types | **Personal Microsoft accounts only** (or "Any Entra ID tenant + personal Microsoft accounts", with the code pinned to `consumers`) | [Quickstart: Register an application](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app) (`ms.date` 2026-05-14) lists "**Personal accounts only** — For apps used only by personal Microsoft accounts (for example: Xbox, Live, Hotmail)" **[DOC]**. [MSAL client application configuration](https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration) (`ms.date` 2025-05-14) notes the effective audience is the intersection of the code's audience and the registration's, and that the only way to sign in *only* personal accounts is to set the registration to work-and-school-plus-personal and the code to `consumers` **[DOC]** |
| Authority / tenant in requests | `https://login.microsoftonline.com/consumers/` | [MSAL client application configuration](https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration): "`https://login.microsoftonline.com/consumers/` — Sign in users with personal Microsoft accounts (MSA) only" **[DOC]**. Minecraft-specific: "You must use the `consumers` AAD tenant to sign in with the `XboxLive.signin` scope" ([minecraft.wiki, Microsoft authentication](https://minecraft.wiki/w/Microsoft_authentication), last modified 2026-09-02) **[COMMUNITY]**. Corroborated by all three launchers hard-coding `/consumers/` **[PRACTICE]** |
| Scopes | `XboxLive.signin offline_access` | ATLauncher `Constants.MICROSOFT_LOGIN_SCOPES = { "XboxLive.signin", "offline_access" }`; HMCL `MicrosoftService.SCOPE = "XboxLive.signin offline_access"` **[PRACTICE]**. Prism uses the variant `XboxLive.SignIn XboxLive.offline_access` **[PRACTICE]** — both work in practice; case is not significant, and `XboxLive.offline_access` vs `offline_access` appear interchangeable here. `offline_access` is what causes a `refresh_token` to be issued ([device code doc](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)) **[DOC]** |
| Client type | **Public client**, no client secret | [minecraft.wiki](https://minecraft.wiki/w/Microsoft_authentication): "obtain an OAuth 2.0 client ID (no client secret needed)" **[COMMUNITY]**; no launcher ships a secret **[PRACTICE]** |
| "Allow public client flows" | **Yes** — required | [Configure desktop apps that call web APIs](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-registration) (`ms.date` 2024-04-09, page updated 2026-06-15): "To distinguish device code flow, integrated Windows authentication, and a username and a password from a confidential client application using a client credential flow used in daemon applications, none of which requires a redirect URI, configure it as a public client application… Under **Manage**, select **Authentication**. Under **Advanced settings**, for **Allow public client flows**, select **Yes**." **[DOC]** |
| Redirect URI | **Not needed for device code flow.** If a loopback/auth-code fallback is ever added: `http://localhost` (system browser) or `https://login.microsoftonline.com/common/oauth2/nativeclient` (embedded browser) | Same doc, "Add a platform redirect URI" **[DOC]**. ATLauncher's auth-code path uses `http://127.0.0.1:28562` **[PRACTICE]** |
| API permissions | **Nothing to add in the portal.** `XboxLive.signin` is not a delegated permission you tick in the Entra admin center; it is requested as a scope string at authorization time | Inferred from the fact that the Feb 2026 developer had "Microsoft Graph permissions (User.Read, offline_access)" configured and still got 403 — the 403 came from Minecraft Services, not from Entra. **Not separately documented; medium confidence** |

**Important:** none of this is what is gating you. The Feb 2026 report is a correctly-configured registration that still fails. The registration is necessary and not sufficient — the allow-list is the sufficient part.

### The registration carries obligations

Prism Launcher's `CMakeLists.txt` carries the comment "By using this key in your builds you accept the terms of use laid down in https://docs.microsoft.com/en-us/legal/microsoft-identity-platform/terms-of-use". That document ([Microsoft identity platform Terms of Use](https://learn.microsoft.com/en-us/legal/microsoft-identity-platform/terms-of-use), "Last revised: May 01, 2019"; page metadata `updated_at` 2025-10-28) binds ash on registration **[DOC]**. The clauses that matter here:

- **§2.1.6** — your Application must "present Users with an End-User-License-Agreement ("EULA") that contains terms consistent with those set forth herein and expressly disclaims all warranties and liability on behalf of Microsoft".
- **§2.1.7** — your Application must present a Privacy Statement that is publicly available online, easily accessible within the Application, and "at least as restrictive regarding the processing of data as Microsoft's Privacy Statement". Phase 1 has no EULA and no privacy statement. **Both are prerequisites, not polish.**
- **§2.1.5** — "not use Microsoft trademarks or tradenames without prior written approval from Microsoft". See §9.
- **§3.1** — "You may not select a Display Name that impersonates someone else… or that may cause confusion. Microsoft reserves the right to reject your Display Name."
- **§3.2** — "Access Credentials are non-transferable and non-assignable. You must keep your Access Credentials confidential, and You may not share your Access Credentials with anyone else." (Relevant to §6: several launchers ship their client ID in public source. A public-client OAuth client ID is not a secret in the OAuth sense, but the wording here is broad.)
- **§1.2.6 / §1.2.7** — don't "use an unreasonable amount of bandwidth, or adversely impact the stability of the Platform"; don't "attempt to circumvent the limitations Microsoft sets on your use of the Platform". This is the closest thing to a documented rate limit (§5).
- **§4.1** — "Microsoft may suspend or immediately terminate this Agreement… and/or may disable your Application or access to the Platform at any time and in Microsoft's sole discretion."

---

## 3. Is the device code flow supported and permitted?

**Yes on both counts, with three practical caveats.**

**Documented [DOC].** [OAuth 2.0 device authorization grant](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code) (`ms.date` 2025-01-04, page updated 2026-06-15) is a first-class, fully documented grant on the Microsoft identity platform, and the `tenant` segment explicitly "can be `/common`, `/consumers`, or `/organizations`" — so `consumers`, which is what Minecraft requires, is supported. The only registration requirement is "Allow public client flows" = Yes (§2).

**Practised [PRACTICE].** All three established open-source launchers examined implement device code against `https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode`:

- Prism Launcher — `launcher/minecraft/auth/steps/MSADeviceCodeStep.cpp`, which even cites the Microsoft doc URL in a comment.
- ATLauncher — `Constants.MICROSOFT_DEVICE_CODE_URL`, driven from `LoginWithMicrosoftDialog`.
- HMCL — `HMCLCore/.../auth/OAuth.java`, `OAuth.MICROSOFT` constructed with the `consumers` devicecode and token URLs.

No documented restriction on the device code flow specific to Minecraft or Xbox Live was found in any source.

### Caveats that hit ash's Phase 1 user stories directly

1. **`verification_uri_complete` is not supported.** The Microsoft doc says so explicitly: "The `verification_uri_complete` response field is not included or supported at this time. We mention this because if you read the standard you see that `verification_uri_complete` is listed as an optional part of the device code flow standard." **[DOC]** So there is no one-click / QR link with the code pre-filled. The UI must show `user_code` and `verification_uri` separately — which is what user stories 7 and 8 already assume. Good; just don't design for the shortcut.
2. **"Notice the moment I approve" (user story 10) is polling, not push.** The client polls `/token` no more often than the `interval` seconds returned in the device authorization response **[DOC]**. Perceived latency is up to `interval` seconds. ATLauncher schedules at exactly `interval` **[PRACTICE]**. Word the UI accordingly.
3. **`slow_down` is not in Microsoft's documented error table.** The documented polling errors are `authorization_pending`, `authorization_declined`, `bad_verification_code`, `expired_token` **[DOC]** — RFC 8628's `slow_down` is absent. ATLauncher handles `authorization_declined`, `expired_token`, `authorization_pending`, and lumps everything else into "unknown error" **[PRACTICE]**. ash should handle `slow_down` defensively anyway (back off and continue) rather than treating it as fatal.

Also note: **`expires_in` defaults to 15 minutes** and "the request should only be made when the user indicates they're ready to sign in" **[DOC]** — which maps cleanly onto user story 11 (expiry → fresh code) and argues for not requesting a device code until the player clicks sign in.

---

## 4. The full authentication chain

Marked per hop. **Hops 1–2 are Microsoft-documented. Hops 3–7 are not documented by anyone first-party** — the shapes below come from `minecraft.wiki` (the de facto community reference, formerly wiki.vg) cross-checked against three independent open-source launcher implementations. Where two implementations disagree, both are shown.

### Hop 1 — Device authorization request **[DOC]**

```http
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode
Content-Type: application/x-www-form-urlencoded

client_id=<ash client id>
&scope=XboxLive.signin%20offline_access
```

Response: `device_code`, `user_code`, `verification_uri`, `expires_in` (default 900), `interval`, `message`.
Source: [v2-oauth2-device-code](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code).

### Hop 2 — Poll for the Microsoft access token **[DOC]**

```http
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:device_code
&client_id=<ash client id>
&device_code=<device_code>
```

Success: `{ token_type: "Bearer", scope, expires_in, access_token, refresh_token, id_token }`. `refresh_token` only if `offline_access` was requested.
Errors while polling: `authorization_pending`, `authorization_declined`, `bad_verification_code`, `expired_token`.

Refresh (same endpoint) uses `grant_type=refresh_token&refresh_token=…&client_id=…` **[PRACTICE]**, per ATLauncher `MicrosoftAuthAPI.refreshAccessToken`.

> Microsoft's own warning, worth heeding for user story 68: "Don't attempt to validate or read tokens for any API you don't own… Tokens for Microsoft services can use a special format that will not validate as a JWT, and may also be encrypted for consumer (Microsoft account) users." **[DOC]** ash must treat the MS access token as opaque.

### Hop 3 — Xbox Live user authentication **[COMMUNITY + PRACTICE]**

```http
POST https://user.auth.xboxlive.com/user/authenticate
Content-Type: application/json
Accept: application/json

{
  "Properties": {
    "AuthMethod": "RPS",
    "SiteName": "user.auth.xboxlive.com",
    "RpsTicket": "d=<microsoft access_token>"
  },
  "RelyingParty": "http://auth.xboxlive.com",
  "TokenType": "JWT"
}
```

Response: `{ "IssueInstant", "NotAfter", "Token": "<xbl token>", "DisplayClaims": { "xui": [ { "uhs": "<userhash>" } ] } }`.

Note the `d=` prefix on the ticket. Identical in [minecraft.wiki](https://minecraft.wiki/w/Microsoft_authentication), HMCL `MicrosoftService`, ATLauncher `MicrosoftAuthAPI.getXBLToken`, Prism `XboxUserStep.cpp`.

### Hop 4 — XSTS authorization for Minecraft **[COMMUNITY + PRACTICE]**

```http
POST https://xsts.auth.xboxlive.com/xsts/authorize
Content-Type: application/json
Accept: application/json

{
  "Properties": { "SandboxId": "RETAIL", "UserTokens": ["<xbl token>"] },
  "RelyingParty": "rp://api.minecraftservices.com/",
  "TokenType": "JWT"
}
```

Response: same shape as hop 3 — `Token` (the XSTS token) plus `DisplayClaims.xui[0].uhs`.

**Error handling matters here** and maps to real, user-facing outcomes. HTTP 401 with `{ "Identity": "0", "XErr": <code>, "Message": "", "Redirect": "…" }`. Known `XErr` codes **[COMMUNITY]**:

| XErr | Meaning |
| --- | --- |
| 2148916227 | Account banned from Xbox |
| 2148916233 | No Xbox account exists for this Microsoft account |
| 2148916235 | Country where Xbox Live is unavailable |
| 2148916236 / 2148916237 | Adult verification required (South Korea) |
| 2148916238 | Child account (under 18) not in a Family group |

Prism maps these to specific user-facing messages, and its source notes some codes "were copied from https://github.com/PrismarineJS/prismarine-auth/pull/44" **[PRACTICE]** — i.e. even the launchers are working from community reverse-engineering. These belong in ash's typed error enum; several are unfixable-by-retry and need distinct copy.

### Hop 5 — Minecraft Services login **[COMMUNITY + PRACTICE]** — two variants in live use

**Variant A — `/launcher/login`** (Prism `LauncherLoginStep.cpp`, ATLauncher `MicrosoftAuthAPI.loginToMinecraft`):

```http
POST https://api.minecraftservices.com/launcher/login
Content-Type: application/json
Accept: application/json

{ "xtoken": "XBL3.0 x=<uhs>;<xsts token>", "platform": "PC_LAUNCHER" }
```

**Variant B — `/authentication/login_with_xbox`** (HMCL `MicrosoftService`, minecraft.wiki):

```http
POST https://api.minecraftservices.com/authentication/login_with_xbox
Content-Type: application/json
Accept: application/json

{ "identityToken": "XBL3.0 x=<uhs>;<xsts token>" }
```

Response (documented for variant B; variant A returns the equivalent):

```json
{ "username": "<uuid string>", "roles": [], "access_token": "<minecraft jwt>", "token_type": "Bearer", "expires_in": 86400 }
```

**This is the hop that returns `403 Invalid app registration` for a non-allow-listed client id.** No first-party statement exists on which variant is preferred or supported; both are shipping in widely used launchers today. Recommendation: pick one, put it behind the HTTP port so it is a one-line change, and record which one the fixtures were captured against.

### Hop 6 — Entitlement check **[COMMUNITY + PRACTICE]** — two variants in live use

**Variant A — `/entitlements/license`** (Prism `EntitlementsStep.cpp`, ATLauncher `getEntitlements`):

```http
GET https://api.minecraftservices.com/entitlements/license?requestId=<random uuid v4>
Authorization: Bearer <minecraft access token>
```

Prism's source carries `// TODO: check presence of same entitlementsRequestId?` — i.e. nobody has confirmed whether the service echoes the request id back **[PRACTICE]**.

**Variant B — `/entitlements/mcstore`** (HMCL, minecraft.wiki):

```http
GET https://api.minecraftservices.com/entitlements/mcstore
Authorization: Bearer <minecraft access token>
```

Response shape **[COMMUNITY]**:

```json
{
  "items": [
    { "name": "product_minecraft", "signature": "<jwt>" },
    { "name": "game_minecraft",    "signature": "<jwt>" }
  ],
  "signature": "<jwt>",
  "keyId": "1"
}
```

Each `signature` is an RS256 JWT whose payload includes `signerId`, `entitlements[]`, `nbf`/`exp`/`iat` and `platform` (e.g. `PC_LAUNCHER`). minecraft.wiki publishes Mojang's public key and states "The signature should always be checked with the public key from Mojang" **[COMMUNITY]**. An account with no copy of the game returns an empty `items` array.

For ash this is the mandatory check the spec calls out. Note that it must distinguish three outcomes — signed in and entitled, signed in and **not** entitled, and sign-in failed — and only this hop can tell you the middle one.

### Hop 7 — Minecraft profile **[COMMUNITY + PRACTICE]**

```http
GET https://api.minecraftservices.com/minecraft/profile
Authorization: Bearer <minecraft access token>
```

Success:

```json
{
  "id": "986dec87b7ec47ff89ff033fdb95c4b5",
  "name": "HowDoesAuthWork",
  "skins": [ { "id": "…", "state": "ACTIVE", "url": "http://textures.minecraft.net/texture/…", "variant": "CLASSIC", "alias": "STEVE" } ],
  "capes": [ … ]
}
```

`id` is the **undashed** Minecraft profile UUID — this is ADR-0009's account key. Note the format: ash must normalise it consistently.

No copy owned → HTTP 404 with `{ "path": "/minecraft/profile", "error": "NOT_FOUND", "errorMessage": "The server has not found anything matching the request URI" }` **[COMMUNITY]**. HMCL and ATLauncher both handle a 404 here as "no profile" **[PRACTICE]**.

**Game Pass caveat, relevant to user story 13 [COMMUNITY, unverified]:** minecraft.wiki states "Xbox Game Pass users who haven't logged into the new Minecraft Launcher at least once will not return a profile." If true, ash must produce a distinct, actionable message for this case ("sign into the official launcher once first") rather than the generic "you don't own Minecraft". Worth confirming empirically once approval lands.

### Documented vs reverse-engineered, at a glance

| Hop | Endpoint | Status |
| --- | --- | --- |
| 1 | `login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` | Microsoft-documented **[DOC]** |
| 2 | `login.microsoftonline.com/consumers/oauth2/v2.0/token` | Microsoft-documented **[DOC]** |
| 3 | `user.auth.xboxlive.com/user/authenticate` | Not documented for this use. Community + shipped code |
| 4 | `xsts.auth.xboxlive.com/xsts/authorize` | Not documented for this use. Community + shipped code |
| 5 | `api.minecraftservices.com/launcher/login` or `/authentication/login_with_xbox` | **No first-party documentation of any kind** |
| 6 | `api.minecraftservices.com/entitlements/license` or `/entitlements/mcstore` | **No first-party documentation of any kind** |
| 7 | `api.minecraftservices.com/minecraft/profile` | **No first-party documentation of any kind** |

The practical consequence: hops 5–7 can change without notice and without a changelog, because there is no published contract to break. The spec's decision to put all network access behind an injected HTTP port with recorded fixtures is the right hedge; the fixtures are the only contract ash will have.

---

## 5. Rate limits, throttling, caching and terms of use

### For `api.minecraftservices.com`

**No published rate limits.** None on `help.minecraft.net` (its article search returns nothing on the topic), none on `minecraft.net`, none on `learn.microsoft.com`. **[Gap 10.]**

**Observed in practice [PRACTICE]:** HTTP 429 is returned. [MultiMC issue #5713](https://github.com/MultiMC/Launcher/issues/5713) (opened 2026-01-13) records `Error transferring https://api.minecraftservices.com/launcher/login - server replied: Too Many Requests`, HTTP 429, on the login hop after Xbox and XSTS both succeeded. No maintainer response; no public explanation of the trigger, the window, or whether it is per-account, per-IP or per-client-id. Related MultiMC/Prism issues record 503 and 504 from the same host — the service is not always available.

**Design consequence for ash:** treat 429 and 5xx from `api.minecraftservices.com` as retryable-with-backoff and surface them as a distinct typed error ("Minecraft services are busy") rather than folding them into the auth-failed path. This matters for user stories 15 and 65: a 429 during a silent refresh must not present as "your session is dead, sign in again".

### For the Microsoft identity endpoints

**Documented [DOC].** From [Understanding client and server throttling in MSAL.NET](https://learn.microsoft.com/en-us/entra/msal/dotnet/advanced/client-and-server-throttling) (`ms.date` 2025-05-20, page updated 2025-07-18):

> Microsoft Entra ID throttles applications when you call the authentication API too frequently. Most often this happens when token caching is not used…
>
> If the server is having problems or if an application is requesting tokens too often Microsoft Entra ID will respond with `HTTP 429 (Too Many Requests)` and with `Retry-After` header, `Retry-After X seconds`… The throttling state is maintained for X seconds. **This limit affects all flows.**
>
> If Microsoft Entra ID is having problems it may respond with a `HTTP 5xx` error code with no `Retry-After` header. The throttling state is maintained for one minute. **Affects only public client flows.**

The doc names the root cause plainly: **"The most likely culprit is that you have not setup token caching."** ash's refresh-token-in-the-Windows-credential-store design already satisfies this, provided ash does not re-run the chain on every launch when the cached Minecraft token (24h lifetime) is still valid.

No public numeric limit for the token endpoint exists. Honour `Retry-After`; that is the documented contract.

### Terms-of-use constraints on the endpoints

From the [Microsoft identity platform Terms of Use](https://learn.microsoft.com/en-us/legal/microsoft-identity-platform/terms-of-use) **[DOC]**, the clauses that bear on how ash calls these APIs:

- §1.2.4 — do not "scrape, build databases or otherwise create copies of any data accessed or obtained using the Platform, except as necessary to enable an intended usage scenario". ash caching a player's own profile/UUID for its own launcher is squarely within an intended usage scenario; harvesting profiles at scale into the ash backend is not.
- §1.2.5 — "request from the Platform more than the minimum amount of data, or more than the minimum permissions… that your Application needs". Argues for `XboxLive.signin offline_access` and nothing else. Do not add Graph scopes.
- §1.2.6 — do not "use an unreasonable amount of bandwidth, or adversely impact the stability of the Platform".
- §1.2.11 — do not "request or make available any data obtained using the Platform outside any permissions expressly granted by Users". Relevant when the Phase 4 backend starts storing profile UUIDs.
- §2.2 — "You must implement proper retention and deletion policies, including deleting all data when your User abandons your Application, uninstalls your Application, closes its account with You, or abandons the account." This is a **backend design requirement** for Phase 4, and it aligns with Phase 1 user story 21 (sign out removes stored tokens).

**Caching requirements:** none stated as a rule. Implied strongly by the throttling doc (cache tokens or get throttled).

---

## 6. What established launchers actually do

This is the strongest evidence available, and it says something specific: **every launcher with working access predates the allow-list policy.** None of them is proof that a new applicant gets in.

### Do they ship their own client id in source?

| Launcher | Client id in public source? | Value / mechanism | Evidence |
| --- | --- | --- | --- |
| **Prism Launcher** | **Yes** | `Launcher_MSA_CLIENT_ID "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"` in `CMakeLists.txt` (branch `develop`) | [PrismLauncher/PrismLauncher `CMakeLists.txt`](https://github.com/PrismLauncher/PrismLauncher/blob/develop/CMakeLists.txt) **[PRACTICE]** |
| **ATLauncher** | **Yes** | `MICROSOFT_LOGIN_CLIENT_ID = "90890812-00d1-48a8-8d3f-38465ef43b58"` in `Constants.java` | [ATLauncher/ATLauncher `Constants.java`](https://github.com/ATLauncher/ATLauncher/blob/master/src/main/java/com/atlauncher/constants/Constants.java) **[PRACTICE]** |
| **MultiMC** | **Yes** | `Launcher_MSA_CLIENT_ID "499546d9-bbfe-4b9b-a086-eb3d75afb78f"` in `CMakeLists.txt` — but the project debrands unofficial builds (see below) | [MultiMC/Launcher `CMakeLists.txt`](https://github.com/MultiMC/Launcher/blob/develop/CMakeLists.txt) **[PRACTICE]** |
| **HMCL** | **No** | Injected at build time from the `MICROSOFT_AUTH_ID` environment variable, defaulting to `""`; the code checks `StringUtils.isBlank(getClientId())` and refuses. Unofficial builds have no Microsoft auth. | [HMCL-dev/HMCL `HMCL/build.gradle.kts`](https://github.com/HMCL-dev/HMCL/blob/main/HMCL/build.gradle.kts), `HMCL/src/main/java/org/jackhuang/hmcl/game/OAuthServer.java` **[PRACTICE]** |

Prism's `CMakeLists.txt` frames the ethics explicitly **[PRACTICE]**:

> `# NOTE: These API keys are here for convenience. If you rebrand this software or intend to break the terms of service of these platforms, please change these API keys beforehand.`
> `# Be aware that if you were to use these API keys for malicious purposes they might get revoked, which might cause breakage to thousands of users.`
> `# By using this key in your builds you accept the terms of use laid down in https://docs.microsoft.com/en-us/legal/microsoft-identity-platform/terms-of-use`

MultiMC's `README.md` is the sharpest statement of the constraint **[PRACTICE]**:

> "Because of the nature of the agreements required to interact with the Microsoft identity platform, it's impossible for us to continue allowing everyone to build the code as 'MultiMC'. The source code has been debranded and now builds as `DevLauncher` by default. You must provide your own branding if you want to distribute your own builds."

That policy is the origin of the PolyMC → Prism lineage: Prism's own FAQ says the PolyMC fork "was primarily a result of continuing disagreements… mainly surrounding the topics of 3rd party packaging and re-distribution. Many users… were left dissatisfied by MultiMC's policy of restricting self-built packages, by not allowing the launcher to build with the necessary API keys for the successful authentication of Microsoft Accounts" ([Prism Launcher FAQ](https://prismlauncher.org/wiki/overview/faq/)) **[PRACTICE]**.

### Did they go through an approval process? Is there a public record?

**No public record of any of them applying, and the dates say they did not have to.**

Client-id vintage, established by reading `CMakeLists.txt` / `Constants.java` at historic tags (all verified 2026-09-09) **[PRACTICE]**:

| Project | Client id | Present at tag | Tag date |
| --- | --- | --- | --- |
| PolyMC (Prism's ancestor) | `17b47edd-c884-4997-926d-9e7f9a6b4647` | 1.0.0 | 2021-12-28 |
| PolyMC | `549033b2-1532-4d4e-ae77-1bbaa46f9d74` | 1.2.0 → 1.4.0 | 2022-04-17 → 2022-07-23 |
| **Prism Launcher (current)** | `c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb` | 6.0 | **2022-12-12** |
| **ATLauncher (current)** | `90890812-00d1-48a8-8d3f-38465ef43b58` | v3.4.13.0 | **2022-03-29** |

Both current client ids were in production **before 2023-05-30**, the date Mojang announced the allow-list. They fall inside "Existing applications, such as launchers and websites, will continue to have access without interruption." (MultiMC and HMCL are older still; their client-id registration dates were not separately verified.)

**So: the fact that Prism, ATLauncher, HMCL and MultiMC all work today tells you nothing about whether a 2026 applicant gets approved.** They are grandfathered. ash is not. This is the single most important inference in this document, and it cuts against the intuitive read of "look, lots of launchers do this".

The closest thing to a new applicant on the public record is the [Aug 2026 HyperKraft report](https://learn.microsoft.com/en-us/answers/questions/5989335/minecraft-services-returns-http-403-invalid-app-re) — a third-party Java launcher, blocked at hop 5, no answer.

### Has anyone been blocked or had access revoked?

**No confirmed case found** of an established launcher losing allow-list access. Searched issue trackers, release notes and news pages for Prism, MultiMC, ATLauncher and PolyMC.

What was found instead:

- **Outages, not revocations.** Prism's news post ["Microsoft authentication issues in some regions"](https://prismlauncher.org/news/login-isues-10-06-24/) (2024-06-09) attributes login failures to "Microsoft's third party auth servers experiencing outages in some regions", notes the official launcher and minecraft.net were affected too, and references Mojang bug WEB-7179. It does **not** mention app registration, client ids, blocking or throttling **[PRACTICE]**.
- **Transient service errors.** MultiMC/Prism issues record 429, 503 and 504 from `api.minecraftservices.com` (§5).
- **The policy's own stated motive is phishing launchers** — "some attempt to maliciously exploit our users through methods such as phishing attempts" **[DOC]**. That is what the allow-list is aimed at, and it is what an application from ash will implicitly be screened against. It argues for making ash's identity, publisher, website and privacy policy unambiguous and verifiable before applying.

Note also that Mojang retains a revocation lever independent of the API: the EULA says "we may takedown Mods or other software that violate our Minecraft Usage Guidelines" **[DOC]**, and the Usage Guidelines say "All permissions and consents are given by us at our discretion and may be revoked at any time if we think that it is appropriate to do so, or we don't like what you are doing" **[DOC]**.

---

## 7. What the EULA and Usage Guidelines say about launchers and modified clients

Sources: [Minecraft EULA](https://www.minecraft.net/en-us/eula) and [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines), both as served 2026-09-09. Neither page carries a visible revision date (gap 11). The last announced substantive change was 2023-08-02 (see below).

### There is no longer a separate "Commercial Usage Guidelines"

Per [Minecraft EULA and Commercial Usage Guidelines Updates](https://www.minecraft.net/en-us/article/minecraft-eula-and-commercial-usage-guidelines-updates) (published 2023-08-02) **[DOC]**:

> "Second, we updated our Commercial Usage Guidelines and Brand and Asset Guidelines… we have decided to merge the different guidelines into a brand-new **Minecraft Usage Guidelines**."

So there are exactly **two** documents to comply with: the EULA and the Usage Guidelines. Anyone still citing separate Commercial Usage Guidelines or Brand Guidelines is citing something retired in 2023.

### The EULA, on tools and launchers **[DOC]**

From the summary at the top:

> "You may develop tools, plug-ins and services as long as they do not seem official or approved by us, such as by using our logos."
> "Do not distribute or make commercial use of anything we've made without our permission."

From "USING mods":

> "By 'Mods,' we mean something original that you or someone else created that doesn't contain a substantial part of our copyrightable code or content. When you combine your Mod with Minecraft: Java Edition, we will call that combination a 'Modded Version' of the game. **We have the final say on what constitutes a Mod and what doesn't. You may not distribute any Modded Versions of our game or software**… Basically, Mods are okay to distribute; hacked versions or Modded Versions of the game client or server software are not okay to distribute."

And, directly on point for a launcher:

> "**In order to ensure the integrity of our games, we need all game downloads and updates to come from a source that we authorize.** It's also important for us that 3rd party tools/services don't seem 'official' as we can't guarantee their quality… Make sure that you read through our Minecraft Usage Guidelines too, as we may takedown Mods or other software that violate our Minecraft Usage Guidelines."

**How ash lands against this:**

- ash **downloads game files from Mojang's own manifest and CDN** and verifies published hashes (Phase 1, "Preparing an instance"). That is the authorized source. Good.
- ash **must not redistribute the game itself.** The depot is a local cache built from Mojang's CDN, not a mirror ash serves. Keep it that way — no ash-hosted jar mirror, ever, however tempting for download speed.
- ash's **client** (Phase 2/3) is a Fabric mod layer injected at runtime. That is a Mod combined with the game at the player's machine — a "Modded Version" that ash **must not distribute as a combined artifact**. Distributing the loader + mods and assembling on the player's machine is the pattern every established launcher uses and is what "Mods are okay to distribute" permits. Shipping a pre-merged jar would not be.
- Bundled Sodium and Lithium are third-party mods distributed unmodified — that is their authors' licence question, not Mojang's.

### The Usage Guidelines, "Extended functionality and modifications" **[DOC]**

> "You may create, use, or distribute a mod if:
> - You distribute the mod **only** and not a modded version of Minecraft
> - The mod doesn't create a play-to-earn function where players earn real-world or out-of-game currency or in-game currency that can be cashed out for real-world currency
> - **The mod cannot be used to directly or indirectly verify whether a player owns or has access to out-of-game content, products, or services that affect in-game features and functions**
>
> Basically, we don't want mods that affect players' experience and creates scarcity of in-game content based off out-of-game conditions. For example, a mod that directly or indirectly checks a player owns an NFT to unlock skins, functions, or other in-game experiences is not ok with us."

### "Essential guidelines" — apply to everything ash ships **[DOC]**

> "If you are using any part of any name, any part of our brand, or any of our assets, then:
> - Do **not** do anything or include anything that makes people think that what you are sharing could be interpreted as official or approved by, endorsed by, associated with, supported by, or connected to us
> - Do **not** redistribute our games or any alterations of our games or game files
> - Do **not** make commercial use or commercially exploit anything that we have made unless these guidelines say it's okay
> - Do **not** give access to anything we've made in a way that is unfair or unreasonable
> - Do **not** pretend to be / associated with / supported by Mojang or Microsoft and make it clear:
>   - You (not us) are responsible for the product or service…
>   - Who the publisher, manufacturer, seller, organizer and/or owner are
>   - Whom to contact about the product, service, or any related purchases and the contact method (**chat and forum links are not acceptable methods**)
> - **Prominently include the disclaimer similar to the following: "NOT AN OFFICIAL MINECRAFT [PRODUCT/SERVICE/EVENT/etc.]. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT"** on your product, listing, description, website/webpage, and all other related materials."

That disclaimer is a hard requirement on the launcher UI, the website, the store page and marketing — not a footer afterthought.

And the catch-all:

> "If something isn't covered by these guidelines and we haven't otherwise said it's okay, that probably means we don't want you to do it. In any case if it isn't covered, please don't do it without getting written permission from us."

---

## 8. Charging money — and the specific problem with client-side paid cosmetics

**This is the finding most likely to invalidate the business model, and it is worse than the API gating because there is no form for it.**

### The prohibition

Usage Guidelines, "Extended functionality and modifications" **[DOC]** — third bullet, quoted in full above:

> "**The mod cannot be used to directly or indirectly verify whether a player owns or has access to out-of-game content, products, or services that affect in-game features and functions**… we don't want mods that affect players' experience and creates scarcity of in-game content based off out-of-game conditions."

Map ash's own domain vocabulary onto that sentence (from `CONTEXT.md`):

- **ash account** — an out-of-game account, separate from the Minecraft profile.
- **Entitlement** — "the grant of one cosmetic to one ash account", sold for money. An **out-of-game product**.
- **Equipped cosmetic** — "Requires a matching entitlement." The client verifies the entitlement.
- **Cosmetic** — "Rendered client-side; the Minecraft server never sees it." An **in-game feature/function** as the player experiences it.

The ash client would therefore *directly verify whether a player has access to an out-of-game product, in order to affect an in-game feature*. On the plain text, that is the prohibited pattern. The NFT example in the guidelines is an *example* — "a mod that directly or indirectly checks a player owns an NFT to unlock skins, functions, or other in-game experiences" — not the boundary of the rule. The rule as written is about **out-of-game ownership gating in-game content**, and payment method is irrelevant to it.

The rationale is stated twice and is about scarcity, not blockchain. From [Minecraft and NFTs](https://www.minecraft.net/en-us/article/minecraft-and-nfts) (published 2022-07-20) **[DOC]**: "In our Minecraft Usage Guidelines, we outline how a server owner can charge for access, and that **all players should have access to the same functionality**. We have these rules to ensure that Minecraft remains a community where everyone has access to the same content." And from the 2023-08-02 update announcement **[DOC]**: "Instead of focusing on a specific technology, we are focusing on fairness and the experience our players should have."

### The EULA adds a second, independent problem

EULA, "USING mods" **[DOC]**:

> "Any Mods you create for Minecraft: Java Edition from scratch belong to you… and you can do whatever you want with them, **as long as you don't sell them for money / try to make money from them** and so long as you don't distribute Modded Versions of the game."

Selling cosmetics that only function inside the ash client is, on a natural reading, trying to make money from the Mod. Note this clause has no NFT qualifier and no scarcity qualifier — it is flat.

Reinforced by the EULA summary — "Do not distribute or make commercial use of anything we've made without our permission" — and by the Usage Guidelines' definition of commercial use, which is unusually broad **[DOC]**:

> "commercial use means any uses of our name, brand, or assets that you use and share with others (**regardless of whether you receive payment or provide it for free**)."

Under that definition ash is a commercial use the moment it is published, free or not.

### What the guidelines *do* permit charging for

For completeness, the monetisation models the guidelines explicitly bless — none of which is ash's **[DOC]**:

- **Servers.** "You may even charge for access to the server", subject to conditions, including "Must only be granted to users who have a genuine paid-for version of Minecraft", and donations are allowed "so long as you don't offer the donor something that only they can use. However, you may offer all players server wide rewards if donation goals are met." Note the shape of that rule: **paid perks that only the payer can use are exactly what is disallowed**, even on servers. It is the same fairness principle as the mod bullet.
- **Videos and streams.** Ad revenue is fine if all videos are free to view; charging viewers directly is not.
- **Hand-crafted physical products**, capped at 20 items per design and **$5,000 USD per calendar year**.

There is no permitted category for "sell digital cosmetics that a client-side mod unlocks".

### Honest assessment

The plain reading of two independent clauses — the EULA's "don't sell them for money" and the Usage Guidelines' out-of-game-verification bullet — is that ash's paid cosmetics plan is **not permitted by the published terms**.

Counter-considerations, stated fairly:

- Lunar Client and Badlion have sold client-side cosmetics for years at large scale (§10). Whatever Mojang's enforcement posture is, it evidently has not extinguished that market. **This is not evidence of permission** — it is evidence of non-enforcement, of a private arrangement, or of a reading of the terms not visible from outside.
- Mojang reserves the right to grant written permission: "If something isn't covered… please don't do it without getting written permission from us" **[DOC]**, and there is a partnership proposal route (§Action items).
- Mojang explicitly declines to pre-clear specific projects: "We are not able to give advice about whether a specific project does or does not comply with these guidelines. If you are unsure, you should speak to an attorney for help" **[DOC]**. So a definitive answer will come from a lawyer, from a partnership conversation, or from an enforcement email — not from a support ticket.

This needs a decision before Phase 4 is designed and, arguably, before Phase 1's positioning and branding are locked. It does not block Phase 1 engineering.

---

## 9. Branding, naming and attribution

All **[DOC]**, from the [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) unless noted.

**Naming.** "You may use the Minecraft name in a secondary name, secondary title, or description if you: Do so because it is necessary to describe your creations or their purpose honestly and fairly; Ensure that the secondary title (which includes a Minecraft name) is not the dominant element or the distinctive part of the complete name or title; Don't use any other aspect of any of our brand or assets as part of any related branding, including as a logo or part of a logo…"

> "You may not use the Minecraft name as the primary or dominant name or title."

Worked examples given: "'The Shaft – a Minecrafter's podcast' (we're cool with this)"; "'Minecraft – the ultimate help app' (we're not cool with this)".

**"ash" is a clean primary name.** Acceptable secondary usage: *"ash — a launcher and client for Minecraft: Java Edition"*. Not acceptable: *"Minecraft ash Client"*, *"ash Minecraft Launcher"* as the product name, or "Minecraft" leading any store/app listing title.

**Definitions to be aware of** — "our name" includes "the name of any one of our games, taglines, features, events, or company identity. We also mean **any names which are confusingly similar to our name**"; "our brand" includes "any names, related logos, fonts, textures, and any other distinctive characteristics"; "our assets" includes "the code, software, graphics, textures, images, models, sounds and other audio from any of our games and any videos or screenshots taken from our games".

**Consequences for the Phase 1 branding pass and for the client's presentation layer:**

- **No Minecraft logo, no Minecraft-style lettering, no Minecraft fonts or textures anywhere in ash's own branding.** The ADR-0006 presentation layer must not lift vanilla textures for ash's own UI chrome (rendering the game's own assets in-game is a different thing; ash's marketing and launcher chrome is what this covers).
- **Screenshots are permitted** as assets under the general video/screenshot allowances, with the disclaimer and the no-official-appearance rule.
- **Required disclaimer**, prominently, on the launcher, the website, the store, and all marketing: *"NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."*
- **Required identification:** who the publisher/seller is, and a real contact method. "Chat and forum links are not acceptable methods" — a Discord invite alone does not satisfy this. An email address or web form does.
- **Domain names.** A domain containing "minecraft" is permitted only if it does not appear official, relates only to Minecraft, and is not registered "for cybersquatting or principally to make money, including through affiliate services". Given ash monetises, **do not register a Minecraft-containing domain.**
- **Microsoft's separate trademark rule.** Microsoft identity platform Terms of Use §2.1.5: your Application must "not use Microsoft trademarks or tradenames without prior written approval from Microsoft" **[DOC]**. And §3.1: the app registration's Display Name must not impersonate or "cause confusion", and Microsoft may reject it. **Register the Azure app as "ash", not as anything containing "Minecraft".** This is a small detail that could sink the allow-list application on first glance.

Also relevant: [Minecraft Help — Content Creation and Broadcasting Terms of Use and Guidelines](https://help.minecraft.net/hc/en-us/articles/4408934440589) (created 2021-09-10, body last edited 2026-03-13): "Fan art, logos, videos, and screen shots are acceptable if they are not used to falsely represent Mojang Studios Minecraft assets. If you intend to use any portion of the name in relation to services, products, or distribution, you must adhere to the following requirements" — which then defers to the Usage Guidelines and the Microsoft Services Agreement **[DOC]**.

---

## 10. Lunar Client, Badlion, and what their practice does and doesn't prove

**Nothing in the EULA or the Usage Guidelines names Lunar Client, Badlion, or any category resembling them.** There is no carve-out for "competitive clients", no licensed-partner tier described publicly, and no exception to the mod bullets in §8. Searched the EULA, Usage Guidelines, the Minecraft help centre and minecraft.net news.

### How Lunar Client describes its own position

From the [Lunar Client Terms of Service](https://www.lunarclient.com/terms) (last updated **2025-10-16**) **[PRACTICE — a third party's self-description, not a Mojang statement]**:

- Affiliation disclaimer, in caps: *"THE LUNAR CLIENT NOR MOONSWORTH LLC IS AFFILIATED WITH, ENDORSED BY, OR OTHERWISE CONNECTED TO MOJANG AB OR THE MICROSOFT CORPORATION."* Minecraft, Mojang and Microsoft marks are acknowledged as the property of their owners, with usage not implying endorsement. This is exactly the §9 disclaimer pattern.
- Operated by **Moonsworth LLC** — a named legal entity, satisfying the "who is responsible / who is the seller" requirement.
- Cosmetic Items are **licensed, not owned**. Launcher Currency (Lunar Coins, Badlion Points) "HAS NO CASH VALUE AND IS NOT EXCHANGEABLE FOR ANY CURRENCY", is non-refundable and non-transferable, with a $500/24h wallet cap. That framing is a deliberate distance from the "play-to-earn"/"cashed out for real-world currency" bullet in the Usage Guidelines.
- Crucially, on compliance with Mojang: Lunar Client **states it is not a party to any agreement between the user and Microsoft/Mojang, and does not monitor, enforce or control the user's compliance with those agreements.** It pushes that obligation onto the user. It makes **no claim of its own compliance, and no claim of any permission from Mojang.**
- The terms now cover Badlion products alongside Lunar's, indicating Badlion operates under the same corporate umbrella. So the two obvious comparables are one data point, not two.

### What to take from this

1. **It is not evidence of permission.** Lunar Client's own terms carefully assert non-affiliation and decline to assert compliance. No public document — Mojang's or theirs — says Mojang permits their cosmetics business.
2. **It is useful evidence of the compliance surface.** Named LLC, prominent non-affiliation disclaimer, licensed-not-owned cosmetics, no-cash-value currency, no cash-out. If ash proceeds, that is the shape of the paperwork, and it is worth copying deliberately rather than inventing.
3. **It says nothing about API access.** Whether Lunar or Badlion hold allow-listed client ids, and by what route, is not public. They also long predate the 2023 policy.
4. **Scale is a defence and a liability.** A large, established client is expensive to enforce against and has a relationship to protect. A new entrant has neither. Do not assume the risk is symmetric.

---

## 11. The application form, verbatim

Captured 2026-09-11 from <https://aka.ms/mce-reviewappid>. Submitted **2026-09-10** for application `ash`. **[DOC — the form's own text, read off the live page]**

The form does not require sign-in and states it "will not automatically collect your details like name and email address unless you provide it yourself."

### Preamble

> Welcome to our AppID review form. This platform serves as a tool for both requesting the approval of a new AppID or reporting an existing AppID that may pose a security threat.
>
> As you may already know, access to certain APIs is managed through AppIDs. If you are currently developing a new application or website and require access to these APIs, you will need to be formally added to our system through this form. On the other hand, if you are aware of a known bad app or website, such as those involved in phishing activities, please do not hesitate to report it along with any relevant details.
>
> Prior to commencing the development of your application, it is recommended that you carefully review our End User License Agreement (EULA) and Usage Guidelines to ensure compliance with our policies and standards. **Any applications that bypass security/auth/license checks or disable safety features will not be approved.**
>
> Note for new apps - **Your application name cannot include Mojang, Minecraft, Microsoft, Live, Xbox, Discord, or Hypixel.**
>
> **Submissions are reviewed weekly, multiple submissions will not make the process go faster.** Thanks!

### The nine questions

1. **I verify that I have read and understood the EULA and all Usage Guidelines** (links to `aka.ms/mcusageguidelines`) — Yes / No. *Required.*
2. **Contact Information (Valid email address).** "In order to validate and approve this request we will need to cross reference your contact information in the Azure Portal."
3. **What type of request is this?** — New AppID for Approval / Existing AppID for Review/Report.
4. **Application Name.** "Use the official display name, or enter something that will help define the app." Subject to the naming restriction above.
5. **Application ID.** The Application (Client) ID as a GUID. One AppID per submission.
6. **Tenant ID.** The Directory (Tenant) ID as a GUID. Required for new app approval, optional when reporting.
7. **Associated website or domain.** "Provide a url to where we can find more information about your application, website, or your brand."
8. **Justification.** "Provide a brief overview of the application and why you need access to any apis, or why you believe it should be reviewed. Submissions that do not have a valid justification will not be reviewed."
9. **Any other information that we should be aware of?** Aimed mainly at reports — redirect traces for suspect apps.

### What the form publishes that the help article does not

- **A review cadence: weekly.** The only turnaround information Mojang publishes anywhere.
- **Do not resubmit.** "Multiple submissions will not make the process go faster." A duplicate in what is also the phishing-report queue is actively counterproductive.
- **A broader naming blocklist than the Usage Guidelines imply** — not just Minecraft and Microsoft, but also Live, Xbox, Discord and Hypixel. `ash` is clean on all seven.
- **An explicit disqualifier:** bypassing security, auth or licence checks, or disabling safety features. ADR-0007's refusal to implement an offline path is therefore not only an EULA position and an ethical one, it is a stated condition of approval.
- **Justification is mandatory**, and an inadequate one means the submission is not reviewed at all rather than rejected.
- **Tenant ID is required**, which the help article never mentions.

### One risk this surfaces

Question 2 says contact information is **cross-referenced against the Azure Portal**. If the email given on the form is not associated with the Azure account that owns the app registration, validation may fail with no notification — the form sends no acknowledgement. Worth confirming the submitted address matches the Azure account, and adding it to the app registration's owner or contact fields if not.

---

## Action items

Priority order. Items 1 and 2 are time-sensitive because their duration is unknown and not under ash's control.

1. **Submit the Java Edition Game Service API allow-list request — this week, before writing auth code.** Form: <https://aka.ms/mce-reviewappid>. Do these in order:
   1. Register the Azure app first (you need a client id to submit). Name it **"ash"** — nothing containing "Minecraft" (Microsoft identity platform ToU §3.1, §2.1.5). Personal Microsoft accounts, public client flows = Yes, no secret, scopes `XboxLive.signin offline_access`.
   2. Stand up the minimum credible publisher surface **before** submitting: a website that identifies the publisher, a real contact email (not a Discord invite), a privacy statement, and an EULA. Microsoft identity platform ToU §2.1.6 and §2.1.7 require the last two regardless; the phishing rationale in Mojang's article means the application will be judged on whether ash looks legitimate.
   3. Carry the required disclaimer on that site from day one: "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."
   4. Open the form in a browser, **record every question and the exact submission date in this repo** (append to this document). Nobody has published what it asks; ash should be the source that does, for its own future reference.
2. **Decide the cosmetics question before Phase 4 is designed, and preferably before branding and positioning are locked.** §8 is a plain-text conflict with two independent clauses. Options, in increasing order of cost:
   - Get a lawyer's read on the Usage Guidelines mod bullets and the EULA's "don't sell them for money" clause as applied to client-side cosmetics.
   - Submit a partnership proposal: <https://aka.ms/mc-partnerships> → `https://partnerships.minecraft.net/hc/en-us/requests/new?ticket_form_id=360001261291`. Note this is a **separate** process from the API allow-list and should not be bundled with it — do not put a monetisation question in front of the security review that gates your API access.
   - Design a monetisation model that does not gate in-game presentation on out-of-game purchase (e.g. charge for launcher/desktop features that never affect what is rendered in game). Bluntly: this is the only option clearly inside the published terms.
   - Proceed as Lunar/Badlion do, with eyes open, and accept a revocable position. Record that as an explicit, dated decision if chosen.
3. **Correct ADR-0007.** Its consequence line asserts "real lead time" for Microsoft approval. That is not supported by any primary source — **no lead time is published anywhere**. Reword to something like: "gated behind a Mojang allow-list with no published criteria or turnaround; the application must be submitted before Phase 1 acceptance can be met." Link to this document.
4. **Add a Phase 1 acceptance caveat to `docs/specs/0001-phase-1-launcher-mvp.md`.** The "Manual acceptance, not automated" clause cannot be satisfied until approval lands. Everything else can. Make that dependency explicit rather than discovering it at the end of the phase.
5. **Capture HTTP fixtures for hops 1–4 early, with an unapproved client id.** Those four hops work today. Recording them now de-risks the auth module and means only hops 5–7 are blocked on approval. Hops 5–7 fixtures can be hand-written from §4 and corrected once real responses are available.
6. **Design for the endpoint ambiguity.** Pick `/launcher/login` + `/entitlements/license?requestId=` (the Prism/ATLauncher pairing — two independent implementations, and `PC_LAUNCHER` is the platform ash actually is), put both hops behind the HTTP port, and record in the fixture which variant was captured. §4 documents the alternative for when it changes without notice.
7. **Make 429 and 5xx from `api.minecraftservices.com` a distinct typed error.** Not "auth failed", not "you don't own Minecraft". §5. This directly affects user stories 15, 48 and 65.
8. **Handle `slow_down` in the device-code poll**, even though Microsoft does not document it. Back off and continue; do not treat as fatal. §3.
9. **Do not build a game-file mirror.** Downloads must come from Mojang's manifest and CDN — "we need all game downloads and updates to come from a source that we authorize" (EULA). The depot is a local cache, never a redistribution point. §7.
10. **Never ship a pre-merged modded game jar.** Distribute the loader and mods; assemble on the player's machine. "You may not distribute any Modded Versions of our game or software" (EULA). Relevant from Phase 2 onward. §7.
11. **When the allow-list request is answered — either way — record the outcome and the elapsed time in this document.** It will be the only primary data point the project has, and there is currently no public one anywhere.

---

## Sources

All retrieved 2026-09-09 unless stated.

### Mojang / Minecraft — primary

- [Java Edition Game Service API Review or Application Process](https://help.minecraft.net/hc/en-us/articles/16254801392141-Java-Edition-Game-Service-API-Review-or-Application-Process) — Minecraft Help article 16254801392141. `created_at` 2023-05-30, `edited_at` 2026-03-11, `updated_at` 2026-09-08. Body and metadata read via the help centre's own content API (`https://help-management-prod.azure-api.net/help_center/en-us/articles/16254801392141`) because the page is JavaScript-rendered.
- <https://aka.ms/mce-reviewappid> → `https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-…` — the application/report form. Tenant decodes to Microsoft Corporation, `72f988bf-86f1-41af-91ab-2d7cd011db47`.
- <https://aka.ms/AppRegInfo> → the help article above. The URL returned inside the live 403.
- `enforce@minecraft.net` — contact given in the article.
- [Minecraft End User License Agreement](https://www.minecraft.net/en-us/eula) — no revision date on page.
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) — no revision date on page.
- [Minecraft EULA and Commercial Usage Guidelines Updates](https://www.minecraft.net/en-us/article/minecraft-eula-and-commercial-usage-guidelines-updates) — published 2023-08-02. Documents the merge of the Commercial Usage Guidelines and Brand and Asset Guidelines into the Usage Guidelines.
- [Minecraft and NFTs](https://www.minecraft.net/en-us/article/minecraft-and-nfts) — published 2022-07-20.
- [Content Creation and Broadcasting Terms of Use and Guidelines](https://help.minecraft.net/hc/en-us/articles/4408934440589) — created 2021-09-10, body last edited 2026-03-13.
- [Mods for Minecraft: Java Edition](https://help.minecraft.net/hc/en-us/articles/4409139065613) — created 2021-09-13, body last edited 2026-03-13.
- <https://aka.ms/mc-partnerships> → `https://partnerships.minecraft.net/hc/en-us/requests/new?ticket_form_id=360001261291` — partnership proposal form.
- <https://aka.ms/mce-EnforcementForm> → `https://help.minecraft.net/hc/en-us/request/new?ticket_form_id=360001225811` — guideline violation report form.

### Microsoft identity platform — primary

- [OAuth 2.0 device authorization grant](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code) — `ms.date` 2025-01-04, page `updated_at` 2026-06-15.
- [Configure desktop apps that call web APIs](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-registration) (canonical: `scenario-desktop-app-configuration`) — `ms.date` 2024-04-09, page `updated_at` 2026-06-15. "Enable public client flow".
- [Client application configuration (MSAL)](https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration) — `ms.date` 2025-05-14, page `updated_at` 2026-06-15. Authorities including `consumers`; public-client redirect URIs.
- [How to Register an App in Microsoft Entra ID](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app) — `ms.date` 2026-05-14. Supported account types.
- [Microsoft identity platform Terms of Use](https://learn.microsoft.com/en-us/legal/microsoft-identity-platform/terms-of-use) — "Last revised: May 01, 2019"; page `updated_at` 2025-10-28.
- [Understanding client and server throttling in MSAL.NET](https://learn.microsoft.com/en-us/entra/msal/dotnet/advanced/client-and-server-throttling) — `ms.date` 2025-05-20, page `updated_at` 2025-07-18.

### Enforcement evidence (Microsoft-hosted, community-authored)

- [Minecraft Services returns HTTP 403 "Invalid app registration" for HyperKraft third-party Java launcher](https://learn.microsoft.com/en-us/answers/questions/5989335/minecraft-services-returns-http-403-invalid-app-re) — asked 2026-08-30; unanswered as of 2026-09-09.
- [How to get XboxLive.signin permission for Azure App Registration (Minecraft Launcher)](https://learn.microsoft.com/en-gb/answers/questions/5768276/how-to-get-xboxlive-signin-permission-for-azure-ap) — asked 2026-02-09. Accepted answer is from a volunteer moderator / Student Ambassador, **not** Microsoft staff, and conflicts with the Mojang help article.

### Community reference (the only documentation for hops 3–7)

- [Microsoft authentication — Minecraft Wiki](https://minecraft.wiki/w/Microsoft_authentication) — last modified 2026-09-02. Successor to wiki.vg; the de facto reference for these endpoints. **Community, not first-party.**

### Open-source launcher source (evidence of practice)

- [PrismLauncher/PrismLauncher `CMakeLists.txt`](https://github.com/PrismLauncher/PrismLauncher/blob/develop/CMakeLists.txt) and `launcher/minecraft/auth/` (`AuthFlow.cpp`, `steps/MSADeviceCodeStep.cpp`, `steps/MSAStep.cpp`, `steps/XboxUserStep.cpp`, `steps/XboxAuthorizationStep.cpp`, `steps/LauncherLoginStep.cpp`, `steps/EntitlementsStep.cpp`, `steps/MinecraftProfileStep.cpp`) — branch `develop`. Historic tags 1.0.0 (2021-12-28), 1.2.0 (2022-04-17), 1.4.0 (2022-07-23), 6.0 (2022-12-12) read for client-id lineage.
- [ATLauncher/ATLauncher `Constants.java`](https://github.com/ATLauncher/ATLauncher/blob/master/src/main/java/com/atlauncher/constants/Constants.java), `utils/MicrosoftAuthAPI.java`, `gui/dialogs/LoginWithMicrosoftDialog.java` — branch `master`; tags v3.4.13.0 (2022-03-29) and v3.4.20.0 (2022-08-06) read for client-id vintage.
- [HMCL-dev/HMCL](https://github.com/HMCL-dev/HMCL) — `HMCLCore/src/main/java/org/jackhuang/hmcl/auth/microsoft/MicrosoftService.java`, `HMCLCore/src/main/java/org/jackhuang/hmcl/auth/OAuth.java`, `HMCL/src/main/java/org/jackhuang/hmcl/game/OAuthServer.java`, `HMCL/build.gradle.kts` — branch `main`.
- [MultiMC/Launcher `README.md`](https://github.com/MultiMC/Launcher/blob/develop/README.md) and `CMakeLists.txt` — "Forking/Redistributing/Custom builds policy".
- [PolyMC/PolyMC `CMakeLists.txt`](https://github.com/PolyMC/PolyMC/blob/develop/CMakeLists.txt) — client-id lineage.
- [MultiMC issue #5713 — "Cannot log in to account on MultiMC: Error 'Too Many Requests'"](https://github.com/MultiMC/Launcher/issues/5713) — opened 2026-01-13. HTTP 429 on `/launcher/login`.
- [MultiMC issue #4360 — "Consider adding UI for custom client ID"](https://github.com/MultiMC/Launcher/issues/4360) — opened 2021-12-16. Open, no maintainer position recorded on the page.
- [Prism Launcher FAQ](https://prismlauncher.org/wiki/overview/faq/) — origin of the PolyMC fork, and the API-key/packaging dispute.
- [Prism Launcher — "Microsoft authentication issues in some regions"](https://prismlauncher.org/news/login-isues-10-06-24/) — 2024-06-09. Outage, not revocation.

### Third-party commercial comparables

- [Lunar Client Terms of Service](https://www.lunarclient.com/terms) — last updated 2025-10-16. Moonsworth LLC; covers Lunar Coins and Badlion Points.
