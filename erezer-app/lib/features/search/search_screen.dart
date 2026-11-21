import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/common.dart';
import '../../widgets/paged_products.dart';

/// Search the whole shop; results follow the typing after a short pause.
class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _field = TextEditingController();
  Timer? _pause;
  String _query = '';

  @override
  void dispose() {
    _pause?.cancel();
    _field.dispose();
    super.dispose();
  }

  void _typed(String text) {
    _pause?.cancel();
    _pause = Timer(const Duration(milliseconds: 450), () => setState(() => _query = text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    final api = ref.watch(apiProvider);
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: TextField(
          key: const Key('search-field'),
          controller: _field,
          autofocus: true,
          textInputAction: TextInputAction.search,
          onChanged: _typed,
          onSubmitted: (t) {
            _pause?.cancel();
            setState(() => _query = t.trim());
          },
          decoration: InputDecoration(
            hintText: 'Search hoodies, tees, anime…',
            border: InputBorder.none,
            suffixIcon: _field.text.isEmpty
                ? null
                : IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () {
                      _field.clear();
                      setState(() => _query = '');
                    },
                  ),
          ),
        ),
      ),
      body: _query.length < 2
          ? const EmptyState(
              icon: Icons.search,
              title: 'Find your next favourite',
              text: 'Type at least two letters to search the shop.',
            )
          : PagedProductGrid(
              key: ValueKey(_query),
              load: (page) => api.browse(query: _query, page: page),
              emptyTitle: 'Nothing found',
              emptyText: 'Try another word, like "hoodie" or "anime".',
              header: (total) => Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: Text(
                  total == null ? '' : '$total results for "$_query"',
                  style: TextStyle(color: AppColors.muted),
                ),
              ),
            ),
    );
  }
}
