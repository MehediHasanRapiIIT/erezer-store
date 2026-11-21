import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../data/api_client.dart';

/// Product and category pages open inside the tab the customer is on, so the
/// bottom bar stays and Back returns to where they were.
String _tabBase(BuildContext context) {
  final path = GoRouterState.of(context).uri.path;
  for (final tab in ['/categories', '/cart']) {
    if (path.startsWith(tab)) return tab;
  }
  return '';
}

void openProduct(BuildContext context, int id) => context.push('${_tabBase(context)}/p/$id');

void openCategory(BuildContext context, int id) => context.push('${_tabBase(context)}/c/$id');

void openSearch(BuildContext context) => context.push('/search');

/// The 3D design studio, full screen.
void openDesignStudio(BuildContext context) => context.push('/design');

/// Follows a banner's button the way the website would: a category's address
/// opens that category, /categories the Categories tab, /product/12 a product.
void openShopLink(BuildContext context, WidgetRef ref, String? link) {
  if (link == null || link.trim().isEmpty) return;
  final uri = Uri.tryParse(link.trim());
  if (uri == null) return;
  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segments.isEmpty) return;
  if (segments.first == 'custom-design') {
    openDesignStudio(context);
    return;
  }
  if (segments.first == 'categories' || segments.first == 'shop') {
    context.go('/categories');
    return;
  }
  if ((segments.first == 'product' || segments.first == 'products') && segments.length > 1) {
    final id = int.tryParse(segments[1]);
    if (id != null) openProduct(context, id);
    return;
  }
  final slug = segments.last;
  final match = ref.read(categoriesProvider).value?.where((c) => c.slug == slug).firstOrNull;
  if (match != null) {
    openCategory(context, match.id);
    return;
  }
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(const SnackBar(content: Text('This opens in a later update of the app.')));
}
