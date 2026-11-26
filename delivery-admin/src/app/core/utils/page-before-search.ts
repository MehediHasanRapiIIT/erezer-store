/**
 * The page a list was on when a search began, so emptying the search box goes back
 * there instead of to the first page.
 *
 *   onSearchChange(q) { this.pageBeforeSearch.typing(q, this.page()); this.search$.next(q); }
 *   this.search$.pipe(debounceTime(300)).subscribe((q) => this.loadPage(this.pageBeforeSearch.pageFor(q)));
 */
export class PageBeforeSearch {
  private saved: number | null = null;

  /** As the search text changes: remembers the page shown when a search starts. */
  typing(query: string, page: number): void {
    if (query.trim() && this.saved === null) this.saved = page;
  }

  /** The page to show: the first while searching, the remembered one once the box is empty. */
  pageFor(query: string, first = 0): number {
    if (query.trim()) return first;
    const back = this.saved ?? first;
    this.saved = null;
    return back;
  }
}
