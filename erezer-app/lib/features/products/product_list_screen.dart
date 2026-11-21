import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/paged_products.dart';

/// A category's products (its sub-categories included), sortable, with its
/// sub-categories as chips across the top.
class ProductListScreen extends ConsumerStatefulWidget {
  const ProductListScreen({super.key, required this.categoryId});
  final int categoryId;

  @override
  ConsumerState<ProductListScreen> createState() => _ProductListScreenState();
}

class _ProductListScreenState extends ConsumerState<ProductListScreen> {
  ProductSort sort = ProductSort.featured;

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(categoriesProvider).value ?? const [];
    final category = all.where((c) => c.id == widget.categoryId).firstOrNull;
    final subs = all.where((c) => c.parentId == widget.categoryId).toList()
      ..sort((a, b) => a.shopOrder.compareTo(b.shopOrder));
    final api = ref.watch(apiProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(category?.name ?? 'Products', style: const TextStyle(fontWeight: FontWeight.w600)),
        actions: [
          IconButton(icon: const Icon(Icons.search), tooltip: 'Search', onPressed: () => openSearch(context)),
          PopupMenuButton<ProductSort>(
            key: const Key('sort-menu'),
            icon: const Icon(Icons.swap_vert),
            tooltip: 'Sort',
            initialValue: sort,
            onSelected: (s) => setState(() => sort = s),
            itemBuilder: (_) => [for (final s in ProductSort.values) PopupMenuItem(value: s, child: Text(s.label))],
          ),
        ],
      ),
      body: PagedProductGrid(
        key: ValueKey(sort),
        load: (page) => api.browse(categoryId: widget.categoryId, sort: sort, page: page),
        emptyTitle: 'No products here yet',
        emptyText: 'New designs are added often. Please check back soon.',
        header: (total) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (subs.isNotEmpty)
              SizedBox(
                height: 48,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: subs.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, i) =>
                      ActionChip(label: Text(subs[i].name), onPressed: () => openCategory(context, subs[i].id)),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Text(
                [if (total != null) '$total products', if (sort != ProductSort.featured) sort.label].join(' · '),
                style: const TextStyle(color: AppColors.muted),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
