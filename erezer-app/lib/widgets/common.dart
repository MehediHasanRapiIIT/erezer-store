import 'package:cached_network_image/cached_network_image.dart';
import 'package:material_ui/material_ui.dart';

import '../app/theme.dart';

/// A photo from the shop's media server, with a soft grey box while it loads
/// and the first letter of [fallback] when there is no photo.
class NetImage extends StatelessWidget {
  const NetImage({super.key, required this.url, required this.fallback});
  final String? url;
  final String fallback;

  @override
  Widget build(BuildContext context) {
    final placeholder = Container(
      color: const Color(0xFFF0F0F0),
      alignment: Alignment.center,
      child: Text(
        fallback.isEmpty ? '' : fallback[0].toUpperCase(),
        style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w700, color: AppColors.muted),
      ),
    );
    if (url == null || url!.isEmpty) return placeholder;
    // Shop photos can be several megabytes; each is opened only as large as it
    // shows, which keeps scrolling smooth and memory low.
    return LayoutBuilder(
      builder: (context, box) {
        final ratio = MediaQuery.devicePixelRatioOf(context);
        final width = box.maxWidth.isFinite ? (box.maxWidth * ratio).round() : null;
        return CachedNetworkImage(
          imageUrl: url!,
          fit: BoxFit.cover,
          memCacheWidth: width,
          fadeInDuration: const Duration(milliseconds: 200),
          placeholder: (context, _) => Container(color: const Color(0xFFF0F0F0)),
          errorWidget: (context, _, _) => placeholder,
        );
      },
    );
  }
}

/// Shown when the server can't be reached, with a button to try again.
class ErrorRetry extends StatelessWidget {
  const ErrorRetry({super.key, required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_rounded, size: 40, color: AppColors.muted),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      ),
    );
  }
}

/// A friendly empty page for parts of the app that come in a later phase.
class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.title, required this.text});
  final IconData icon;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: const BoxDecoration(color: Color(0xFFF0F0F0), shape: BoxShape.circle),
              child: Icon(icon, size: 32),
            ),
            const SizedBox(height: 16),
            Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            Text(
              text,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted),
            ),
          ],
        ),
      ),
    );
  }
}
