/**
 * Which permissions need which others, so the checklist never produces a set
 * that makes no sense (e.g. "Cancel orders" without "See orders").
 *
 * Ticking a key also ticks everything it needs; unticking a key also unticks
 * everything that needs it. Needs are followed through (reports.export needs
 * reports.view, which needs finance.revenue).
 */

/** Needs that the generic "see the area first" rule doesn't cover. */
const EXPLICIT_NEEDS: Readonly<Record<string, readonly string[]>> = {
  'reports.view': ['finance.revenue'],
  'reports.export': ['reports.view', 'finance.revenue'],
  'products.create': ['products.price', 'products.view'],
  'newsletter.campaigns.edit': ['newsletter.campaigns.view'],
  'newsletter.campaigns.send': ['newsletter.campaigns.view'],
  'staff.manage': ['staff.view'],
  'staff.delete': ['staff.view'],
  'staff.permissions': ['staff.view'],
};

/** Keys that need nothing else. */
const STANDALONE = new Set(['finance.revenue', 'dashboard.view', 'analytics.view', 'activity.view']);

/** Three-part keys whose area is their first part (orders.notes.add belongs to "orders"). */
const FIRST_PART_GROUPS = ['orders.notes.', 'orders.invoice.', 'newsletter.campaigns.'];

/** The part of a key that names its area: "coupons" for coupons.delete, "orders" for orders.notes.add. */
function areaPrefix(key: string): string {
  if (FIRST_PART_GROUPS.some((g) => key.startsWith(g))) return key.slice(0, key.indexOf('.'));
  const dot = key.lastIndexOf('.');
  return dot > 0 ? key.slice(0, dot) : key;
}

/** What a key needs directly, limited to keys that exist. */
function directNeeds(key: string, known: ReadonlySet<string>): string[] {
  if (STANDALONE.has(key)) return [];
  const needs = new Set(EXPLICIT_NEEDS[key] ?? []);
  // Generic rule: any action in an area needs that area's ".view" key.
  const view = `${areaPrefix(key)}.view`;
  if (view !== key) needs.add(view);
  needs.delete(key);
  return [...needs].filter((k) => known.has(k));
}

export interface PermissionGraph {
  /** Everything this key needs, directly or through another key. */
  needs(key: string): readonly string[];
  /** Everything that needs this key, directly or through another key. */
  neededBy(key: string): readonly string[];
}

export function buildPermissionGraph(keys: readonly string[]): PermissionGraph {
  const known = new Set(keys);
  const direct = new Map(keys.map((k) => [k, directNeeds(k, known)]));

  const needs = new Map<string, string[]>();
  for (const key of keys) {
    const seen = new Set<string>();
    const stack = [...(direct.get(key) ?? [])];
    while (stack.length > 0) {
      const next = stack.pop()!;
      if (next === key || seen.has(next)) continue;
      seen.add(next);
      stack.push(...(direct.get(next) ?? []));
    }
    needs.set(key, [...seen]);
  }

  const neededBy = new Map<string, string[]>(keys.map((k) => [k, []]));
  for (const [key, list] of needs) {
    for (const n of list) neededBy.get(n)?.push(key);
  }

  return {
    needs: (key) => needs.get(key) ?? [],
    neededBy: (key) => neededBy.get(key) ?? [],
  };
}

/** Whether the person using the checklist may give or take away a key. */
export type MayChange = (key: string) => boolean;

export interface TickChange {
  next: Set<string>;
  /** Keys changed automatically along with the one clicked. */
  also: string[];
}

/** Keys `key` needs that are off and can't be ticked by this person, which stops `key` being ticked. */
export function tickBlockers(key: string, ticked: ReadonlySet<string>, graph: PermissionGraph, mayChange: MayChange): string[] {
  return graph.needs(key).filter((k) => !ticked.has(k) && !mayChange(k));
}

/** Ticked keys that need `key` and can't be unticked by this person, which stops `key` being unticked. */
export function untickBlockers(key: string, ticked: ReadonlySet<string>, graph: PermissionGraph, mayChange: MayChange): string[] {
  return graph.neededBy(key).filter((k) => ticked.has(k) && !mayChange(k));
}

/** Ticks `key` and everything it needs. Check tickBlockers first. */
export function tick(key: string, ticked: ReadonlySet<string>, graph: PermissionGraph): TickChange {
  const next = new Set(ticked);
  next.add(key);
  const also: string[] = [];
  for (const k of graph.needs(key)) {
    if (!next.has(k)) {
      next.add(k);
      also.push(k);
    }
  }
  return { next, also };
}

/** Unticks `key` and everything that needs it. Check untickBlockers first. */
export function untick(key: string, ticked: ReadonlySet<string>, graph: PermissionGraph): TickChange {
  const next = new Set(ticked);
  next.delete(key);
  const also: string[] = [];
  for (const k of graph.neededBy(key)) {
    if (next.delete(k)) also.push(k);
  }
  return { next, also };
}

/**
 * The ticks after applying a template. Keys this person may change follow the
 * template; keys they may not change keep their current state (as do keys
 * the catalogue doesn't know). Whatever the result needs is ticked where
 * possible; a template key whose needs can't be met is left out.
 *
 * `skipped` lists template keys that could not be given.
 */
export function applyTemplateTicks(
  current: ReadonlySet<string>,
  template: readonly string[],
  known: ReadonlySet<string>,
  graph: PermissionGraph,
  mayChange: MayChange,
): { next: Set<string>; skipped: string[] } {
  const next = new Set<string>();
  const skipped = new Set<string>();

  for (const k of current) {
    if (!known.has(k) || !mayChange(k)) next.add(k);
  }
  for (const k of template) {
    if (!known.has(k)) continue;
    if (mayChange(k)) next.add(k);
    else if (!next.has(k)) skipped.add(k);
  }

  // Needs are already followed through, so one pass settles everything.
  for (const k of [...next]) {
    if (!known.has(k) || !next.has(k)) continue;
    const needs = graph.needs(k);
    if (mayChange(k) && needs.some((n) => !next.has(n) && !mayChange(n))) {
      next.delete(k);
      skipped.add(k);
      continue;
    }
    for (const n of needs) {
      if (!next.has(n) && mayChange(n)) next.add(n);
    }
  }

  return { next, skipped: [...skipped].filter((k) => !next.has(k)) };
}
