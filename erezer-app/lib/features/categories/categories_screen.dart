import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../widgets/common.dart';

/// Every main category with its photo and product count; tapping one shows
/// its sub-categories, which open their product lists.
class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final categories = ref.watch(categoriesProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Categories', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: categories.when(
        loading: () => const Center(child: CircularProgressIndicator(color: AppColors.ink)),
        error: (e, _) => ErrorRetry(message: e.toString(), onRetry: () => ref.invalidate(categoriesProvider)),
        data: (all) {
          final top = all.where((c) => c.parentId == null).toList()..sort((a, b) => a.shopOrder.compareTo(b.shopOrder));
          return RefreshIndicator(
            color: AppColors.ink,
            onRefresh: () async {
              ref.invalidate(categoriesProvider);
              await ref.read(categoriesProvider.future);
            },
            child: ListView.separated(
              key: const Key('category-list'),
              padding: const EdgeInsets.all(16),
              itemCount: top.length + 1,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, n) {
                if (n == 0) return const _DesignCard();
                final i = n - 1;
                return _CategoryCard(
                  category: top[i],
                  children: all.where((c) => c.parentId == top[i].id).toList()
                    ..sort((a, b) => a.shopOrder.compareTo(b.shopOrder)),
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _CategoryCard extends StatefulWidget {
  const _CategoryCard({required this.category, required this.children});
  final Category category;
  final List<Category> children;

  @override
  State<_CategoryCard> createState() => _CategoryCardState();
}

class _CategoryCardState extends State<_CategoryCard> {
  bool open = false;

  @override
  Widget build(BuildContext context) {
    final c = widget.category;
    final hasChildren = widget.children.isNotEmpty;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          InkWell(
            onTap: hasChildren ? () => setState(() => open = !open) : () => openCategory(context, c.id),
            child: Row(
              children: [
                SizedBox(
                  width: 88,
                  height: 88,
                  child: NetImage(url: c.imageUrl, fallback: c.name),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(c.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 4),
                      Text('${c.productCount} products', style: const TextStyle(color: AppColors.muted)),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(right: 12),
                  child: Icon(
                    hasChildren && open ? Icons.expand_less : (hasChildren ? Icons.expand_more : Icons.chevron_right),
                  ),
                ),
              ],
            ),
          ),
          if (open)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  ActionChip(label: Text('All ${c.name}'), onPressed: () => openCategory(context, c.id)),
                  for (final sub in widget.children)
                    ActionChip(
                      label: Text('${sub.name} (${sub.productCount})'),
                      onPressed: () => openCategory(context, sub.id),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// "Design your own": opens the 3D studio.
class _DesignCard extends StatelessWidget {
  const _DesignCard();

  @override
  Widget build(BuildContext context) {
    return InkWell(
      key: const Key('design-your-own'),
      borderRadius: BorderRadius.circular(16),
      onTap: () => openDesignStudio(context),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: const LinearGradient(colors: [Color(0xFF0A0A0A), Color(0xFF262626)]),
        ),
        child: const Row(
          children: [
            Icon(Icons.brush_outlined, color: Colors.white, size: 32),
            SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Design your own',
                    style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w700),
                  ),
                  SizedBox(height: 2),
                  Text('Make your T-shirt in 3D: add text and pictures.', style: TextStyle(color: Color(0xFFD4D4D4))),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: Colors.white),
          ],
        ),
      ),
    );
  }
}
