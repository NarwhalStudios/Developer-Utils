# Perfect Utils — documentation site

Next.js + [Fumadocs](https://fumadocs.dev) site for the Perfect Utils mod.
Lives at `/docs` in the repo so the Java/Gradle build at the root is
untouched.

## Local development

```bash
cd docs
npm install
npm run dev          # http://localhost:3000
npm run build        # production build (catches MDX parse + link errors)
npm run types:check  # tsc --noEmit
```

## Where things live

| Path                        | What                                                   |
| --------------------------- | ------------------------------------------------------ |
| `app/(home)`                | Landing page (hand-built, not from MDX).               |
| `app/docs`                  | Docs layout + dynamic `[[...slug]]` page renderer.     |
| `app/api/search/route.ts`   | Orama search route handler.                            |
| `content/docs/*.mdx`        | All docs content. Edit these, not anything in `app/`.  |
| `content/docs/meta.json`    | Sidebar order for the top-level pages.                 |
| `content/docs/api/meta.json`| Sidebar order inside the API Reference group.          |
| `lib/shared.ts`             | App name + GitHub coords. Edit if the repo moves.      |
| `lib/source.ts`             | Wires `source.config.ts` into the Fumadocs `loader()`. |
| `source.config.ts`          | Fumadocs MDX config — frontmatter schema, etc.         |

## Editing content

Each MDX file has a frontmatter block:

```mdx
---
title: Page title
description: One-line summary that surfaces in OG cards and sidebar tooltips.
---
```

Adding a new page = drop a new `.mdx` file in `content/docs/` (or under
`content/docs/api/` for the API Reference group) and add its slug to the
relevant `meta.json`.

## Deploying to Vercel

1. Import the repo in Vercel.
2. **Project Settings → General → Root Directory → `docs`.**
3. Framework Preset: Next.js (auto-detected once Root Directory is set).
4. Build command and output directory can stay at their defaults.

That's it. Pushes to `main` deploy production; PRs get preview URLs.
