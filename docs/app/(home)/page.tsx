import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="flex flex-col flex-1">
      <section className="flex flex-col items-center justify-center text-center px-6 py-24 sm:py-32 border-b">
        <p className="text-xs uppercase tracking-widest text-fd-muted-foreground mb-4">
          A library mod for Hytale
        </p>
        <h1 className="text-4xl sm:text-6xl font-bold tracking-tight max-w-3xl">
          Perfect Utils
        </h1>
        <p className="mt-6 text-lg sm:text-xl text-fd-muted-foreground max-w-2xl">
          Reusable gameplay primitives for Hytale mods. Stun a mob, drop your
          aggro, taunt a pack — from a single line of code, with no
          <code className="mx-1 px-1.5 py-0.5 rounded bg-fd-muted text-sm">CommandBuffer</code>
          plumbing.
        </p>
        <div className="mt-10 flex flex-wrap gap-3 justify-center">
          <Link
            href="/docs"
            className="px-5 py-2.5 rounded-md bg-fd-primary text-fd-primary-foreground font-medium text-sm hover:opacity-90 transition"
          >
            Read the docs
          </Link>
          <Link
            href="/docs/api/stun"
            className="px-5 py-2.5 rounded-md border border-fd-border font-medium text-sm hover:bg-fd-muted transition"
          >
            API reference
          </Link>
        </div>
      </section>

      <section className="grid sm:grid-cols-2 gap-px bg-fd-border">
        <FeatureCard
          tag="StunMobAPI"
          title="Mob stun"
          body="Freeze a mob's movement, suppress its combat AI, and lock its interactions for a chosen duration. Full stun and lighter stagger flavors, with re-application per tick so Hytale's short effect expiry doesn't break the lock."
          href="/docs/api/stun"
        />
        <FeatureCard
          tag="AggroAPI"
          title="Mob aggro &amp; taunt"
          body="Three modes against the same in-mod state: a one-shot drop, a sustained ignore-me window, and a taunt that pins nearby mobs to a single target. Bound by radius or world-wide."
          href="/docs/api/aggro"
        />
      </section>

      <section className="px-6 py-20 max-w-4xl mx-auto text-center">
        <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight">
          Built to be a soft dependency
        </h2>
        <p className="mt-4 text-fd-muted-foreground">
          Consume it reflectively from your own mod — no compile-time dependency,
          no classloader pain. When Perfect Utils isn't installed, every call
          becomes a silent no-op and your mod still loads cleanly.
        </p>
        <Link
          href="/docs/consuming"
          className="mt-6 inline-block text-sm font-medium underline underline-offset-4 hover:no-underline"
        >
          See the consumer pattern →
        </Link>
      </section>
    </main>
  );
}

function FeatureCard({
  tag,
  title,
  body,
  href,
}: {
  tag: string;
  title: string;
  body: string;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="block bg-fd-background p-8 hover:bg-fd-muted transition"
    >
      <p className="text-xs font-mono uppercase tracking-wider text-fd-muted-foreground mb-3">
        {tag}
      </p>
      <h3 className="text-xl font-semibold mb-3">{title}</h3>
      <p className="text-sm text-fd-muted-foreground leading-relaxed">{body}</p>
      <p className="mt-4 text-sm font-medium">Learn more →</p>
    </Link>
  );
}
