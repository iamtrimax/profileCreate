# LinkHub interface design

Applied local `.agents/skills/ui-ux-pro-max/SKILL.md`. System search: creator portfolio SaaS minimal. Focused style search: minimalism content first (Minimalism & Swiss Style).

The focused style guidance supersedes the initial glassmorphism suggestion: the requested outcome is lower visual density and content clarity. Use opaque neutral surfaces, one green app accent, 16px form inputs, a 4/8px spacing rhythm, visible keyboard focus, 44px control targets, and reduced-motion support. System fonts keep Vietnamese text legible without a third-party font dependency.

Dashboard navigation separates overview, profile design, content/services, bookings, inbox, and account. Forms stay mounted when switching views, preserving unsaved input. Notifications and upcoming-booking links reveal their destination before focusing it. Hash navigation supports back/forward.

The 15 profile templates share content and palettes between thumbnails, live preview, and published pages. Eight additions: Editorial, Bento, Consultant, Boutique, Brutalist, Terminal, Résumé, Spotlight. Container queries enable wide compositions on public pages while keeping the phone preview single-column. No sample business content is published to user accounts.

V10 expands the database allowlist without modifying existing template choices. The enum and client catalog use matching IDs. Run the migration before deploying assets that submit the new IDs.
