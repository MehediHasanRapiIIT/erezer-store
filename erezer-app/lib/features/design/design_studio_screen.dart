import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

import '../../app/config.dart';
import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../widgets/common.dart';

/// The website's 3D design studio, inside the app. The site hides its own
/// header and footer when opened with ?app=1. Photos for a design come from the
/// phone's gallery; a link that leaves the studio opens the matching app page.
class DesignStudioScreen extends ConsumerStatefulWidget {
  const DesignStudioScreen({super.key});

  @override
  ConsumerState<DesignStudioScreen> createState() => _DesignStudioScreenState();
}

class _DesignStudioScreenState extends ConsumerState<DesignStudioScreen> {
  static final _studio = Uri.parse('$storeUrl/custom-design?app=1');
  late final WebViewController _web = _build();
  int _progress = 0;
  bool _failed = false;

  WebViewController _build() {
    final web = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(AppColors.page)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri == null || _inStudio(uri)) return NavigationDecision.navigate;
            _leaveFor(uri);
            return NavigationDecision.prevent;
          },
          onProgress: (p) {
            if (mounted) setState(() => _progress = p);
          },
          onPageStarted: (_) {
            if (mounted && _failed) setState(() => _failed = false);
          },
          onWebResourceError: (error) {
            // Only the page itself failing matters; a missing picture is not a reason to give up.
            if (error.isForMainFrame ?? true) {
              if (mounted) setState(() => _failed = true);
            }
          },
        ),
      )
      ..loadRequest(_studio);
    // The studio's "Upload image" button: pick a photo from the phone.
    final platform = web.platform;
    if (platform is AndroidWebViewController) {
      platform.setOnShowFileSelector(_pickPhotos);
    }
    return web;
  }

  /// The studio page itself, and anything the site loads for it, stays here.
  bool _inStudio(Uri uri) {
    final site = Uri.parse(storeUrl);
    if (uri.scheme == 'about' || uri.scheme == 'blob' || uri.scheme == 'data') return true;
    if (uri.host != site.host) return false;
    return uri.path.startsWith('/custom-design');
  }

  /// A link elsewhere on the site (or beyond): the app's own page for it.
  void _leaveFor(Uri uri) {
    if (!mounted) return;
    final site = Uri.parse(storeUrl);
    if (uri.host == site.host && (uri.path == '/' || uri.path.isEmpty)) {
      context.go('/');
      return;
    }
    if (uri.host == site.host && uri.path.startsWith('/account')) {
      context.go('/account');
      return;
    }
    openShopLink(context, ref, uri.toString());
  }

  Future<List<String>> _pickPhotos(FileSelectorParams params) async {
    final picker = ImagePicker();
    final many = params.mode == FileSelectorMode.openMultiple;
    final picked = many ? await picker.pickMultiImage() : [?await picker.pickImage(source: ImageSource.gallery)];
    return [for (final f in picked) Uri.file(f.path).toString()];
  }

  /// The phone's Back steps back inside the studio first; leaving it asks, so
  /// one press can't throw away a design.
  Future<void> _back() async {
    if (await _web.canGoBack()) {
      await _web.goBack();
    } else {
      await _close();
    }
  }

  Future<void> _close() async {
    final leave = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Leave the design studio?'),
        content: const Text('A design you haven\'t saved or sent will be lost.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Keep designing')),
          TextButton(
            key: const Key('leave-studio'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Leave'),
          ),
        ],
      ),
    );
    if (leave == true && mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(icon: const Icon(Icons.close), tooltip: 'Close', onPressed: _close),
          title: const Text('Design your own', style: TextStyle(fontWeight: FontWeight.w600)),
          bottom: _progress < 100 && !_failed
              ? PreferredSize(
                  preferredSize: const Size.fromHeight(2),
                  child: LinearProgressIndicator(value: _progress / 100, minHeight: 2, color: AppColors.ink),
                )
              : null,
        ),
        body: _failed
            ? ErrorRetry(
                message: "The design studio didn't open. Please check your internet and try again.",
                onRetry: () {
                  setState(() => _failed = false);
                  _web.loadRequest(_studio);
                },
              )
            : WebViewWidget(key: const Key('design-studio'), controller: _web),
      ),
    );
  }
}
