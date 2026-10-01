# Shared OPL reads

The public entry point is `OplRepository` in `commonMain`. It is registered in
`sharedModules`, so Android can inject it with Koin. Swift can obtain the same
repository through `KoinIosKt.getIosOplRepository()` and call its suspend methods
using the Kotlin framework's completion handlers. No presentation state is owned
by the repository.

| Method | Backend request |
| --- | --- |
| `getBySite(siteId)` | `GET /opl-mstr/site/{siteId}` |
| `search(siteId, query)` | `GET /opl-mstr/site/{siteId}/search?query=...` |
| `getByLevel(siteId, levelId)` | `GET /opl-levels/level/{levelId}` |
| `getById(siteId, oplId)` | `GET /opl-mstr/{oplId}` |

Every call returns `NetworkResult`: success preserves the HTTP status and exposes
domain models; failure preserves the network/HTTP error. An empty search returns
the site's list. Non-empty search trims the query, uses Ktor query-parameter
encoding, and validates the backend's 100-character limit.

Pass the selected authenticated `UserSite.id`. The repository checks session site
membership before requesting data and checks that returned lessons belong to that
site. For `getByLevel`, pass the synchronized `Level.id` (a String); the repository
also checks that the LEVEL exists in that site's local catalog. Synchronize
catalogs first. The server remains responsible for final authorization.

`Opl` exposes objective, type, authors/reviewer, dates, usage metadata, assigned
LEVELS, and ordered `content`. Backend `objetive` maps to `objective`, `details`
maps to `content`, and `oplLevelId` maps to `levelRelationId`. LEVEL IDs are Strings
to match the existing local catalog. The by-LEVEL response contains the selected
LEVEL; use `getById` for all assignments in the detail screen.

Content types map `texto`, `imagen`, `video`, `pdf` to `TEXT`, `IMAGE`, `VIDEO`,
`PDF`. Unknown types preserve their `typeCode` and map to `UNKNOWN`. Text and
`mediaUrl` are preserved exactly, and content is ordered by `order`, then `id`.
Legacy content with a null site inherits its parent lesson's site. Content from
another OPL/site causes a serialization failure rather than being exposed.

The backend currently returns 404 for a site/LEVEL with no OPL assignments, while
search returns a successful empty list when there are no matches. Those 404s stay
as failures; the future presentation layer can decide how to display them without
hiding other missing-resource errors.

These are online reads. There is no OPL persistence or offline media download.
Image/video/PDF URLs are ready for the future native viewers. The shared HTTP
client supplies authentication, token refresh, timeouts and cancellation behavior.
