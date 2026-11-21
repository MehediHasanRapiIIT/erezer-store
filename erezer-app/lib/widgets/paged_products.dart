import 'package:material_ui/material_ui.dart';

import '../app/theme.dart';
import '../data/models.dart';
import 'common.dart';
import '../features/home/home_bands.dart';

/// Loads one page of products; pages count from 0.
typedef PageLoader = Future<PageOf<Product>> Function(int page);

/// A product grid that loads the next page as the customer nears the end.
/// Give it a new key to start again (another sort or search).
class PagedProductGrid extends StatefulWidget {
  const PagedProductGrid({
    super.key,
    required this.load,
    this.header,
    this.emptyTitle = 'Nothing here yet',
    this.emptyText = 'Please check back soon.',
  });

  final PageLoader load;

  /// Shown above the grid; given the number of products found once known.
  final Widget Function(int? total)? header;
  final String emptyTitle;
  final String emptyText;

  @override
  State<PagedProductGrid> createState() => _PagedProductGridState();
}

class _PagedProductGridState extends State<PagedProductGrid> {
  final _items = <Product>[];
  int _page = 0;
  int? _total;
  bool _loading = false;
  bool _done = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || _done) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await widget.load(_page);
      if (!mounted) return;
      setState(() {
        _items.addAll(page.items);
        _total = page.total;
        _page++;
        _done = page.isLast || page.items.isEmpty;
      });
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reload() async {
    setState(() {
      _items.clear();
      _page = 0;
      _total = null;
      _done = false;
    });
    await _loadMore();
  }

  @override
  Widget build(BuildContext context) {
    if (_items.isEmpty && _error != null) return ErrorRetry(message: _error!, onRetry: _loadMore);
    if (_items.isEmpty && !_done) {
      return Center(child: CircularProgressIndicator(color: AppColors.ink));
    }
    return RefreshIndicator(
      color: AppColors.ink,
      onRefresh: _reload,
      child: NotificationListener<ScrollNotification>(
        onNotification: (n) {
          if (n.metrics.extentAfter < 600) _loadMore();
          return false;
        },
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            if (widget.header != null) SliverToBoxAdapter(child: widget.header!(_total)),
            if (_items.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: EmptyState(icon: Icons.search_off, title: widget.emptyTitle, text: widget.emptyText),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                sliver: SliverList.builder(
                  itemCount: (_items.length + 1) ~/ 2,
                  itemBuilder: (_, row) => IntrinsicHeight(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(child: GridProductCard(product: _items[row * 2])),
                        Expanded(
                          child: row * 2 + 1 < _items.length
                              ? GridProductCard(product: _items[row * 2 + 1])
                              : const SizedBox.shrink(),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Center(
                  child: _error != null
                      ? TextButton(onPressed: _loadMore, child: const Text('Couldn’t load more. Tap to try again.'))
                      : _loading
                      ? CircularProgressIndicator(color: AppColors.ink)
                      : const SizedBox.shrink(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
