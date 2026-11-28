import { pageNumbers } from './pager.component';

// Shown as page numbers counted from 1, with … for a gap.
const shown = (current: number, total: number) =>
  pageNumbers(current, total).map((p) => (p === -1 ? '…' : String(p + 1))).join(' ');

describe('pageNumbers', () => {
  it('shows every page when there are few', () => {
    expect(shown(0, 1)).toBe('1');
    expect(shown(4, 9)).toBe('1 2 3 4 5 6 7 8 9');
  });

  it('reaches page 5 straight from the first page', () => {
    expect(shown(0, 31)).toBe('1 2 3 4 5 6 … 31');
  });

  it('shows two pages either side in the middle, with the first and last', () => {
    expect(shown(9, 31)).toBe('1 … 8 9 10 11 12 … 31');
  });

  it('keeps the same number of buttons near the end', () => {
    expect(shown(30, 31)).toBe('1 … 26 27 28 29 30 31');
    expect(shown(28, 31)).toBe('1 … 26 27 28 29 30 31');
  });

  it('has no gap next to a neighbouring page', () => {
    expect(shown(3, 31)).toBe('1 2 3 4 5 6 … 31');
    expect(shown(4, 31)).toBe('1 … 3 4 5 6 7 … 31');
  });
});
