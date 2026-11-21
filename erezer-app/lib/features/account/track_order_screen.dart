import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';
import '../../widgets/order_timeline.dart';
import 'order_detail_screen.dart';
import 'orders_screen.dart';

/// Any order's progress by its number (EZ-482915), for guests too: steps,
/// items and money, and "Pay with bKash" while a bKash order is unpaid.
class TrackOrderScreen extends ConsumerStatefulWidget {
  const TrackOrderScreen({super.key, this.number});
  final String? number;

  @override
  ConsumerState<TrackOrderScreen> createState() => _TrackOrderScreenState();
}

class _TrackOrderScreenState extends ConsumerState<TrackOrderScreen> {
  late final _number = TextEditingController(text: widget.number ?? '');
  PublicTracking? _found;
  String? _error;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    // Opened with a number (from the thank-you page): look it up once the page is up.
    if ((widget.number ?? '').isNotEmpty) WidgetsBinding.instance.addPostFrameCallback((_) => _look());
  }

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  Future<void> _look() async {
    final number = _number.text.trim();
    if (number.isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final t = await ref.read(apiProvider).trackByNumber(number);
      if (mounted) setState(() => _found = t);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _found = null;
          _error = e.statusCode == 404 ? 'We couldn\'t find that order. Please check the number.' : e.message;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = _found;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Track an order', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: RefreshIndicator(
        color: AppColors.ink,
        onRefresh: _look,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const Key('track-number'),
                    controller: _number,
                    textCapitalization: TextCapitalization.characters,
                    textInputAction: TextInputAction.search,
                    onSubmitted: (_) => _look(),
                    decoration: InputDecoration(
                      labelText: 'Order number',
                      hintText: 'EZ-123456',
                      filled: true,
                      fillColor: Colors.white,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: AppColors.line),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                SizedBox(
                  height: 54,
                  child: FilledButton(
                    key: const Key('track-go'),
                    onPressed: _busy ? null : _look,
                    style: FilledButton.styleFrom(
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('Track'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_busy) const Center(child: CircularProgressIndicator(color: AppColors.ink)),
            if (_error != null)
              Text(
                _error!,
                key: const Key('track-error'),
                style: const TextStyle(color: Color(0xFFB91C1C)),
              ),
            if (t != null && !_busy) ...[
              Row(
                children: [
                  Expanded(
                    child: Text(t.orderNumber, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                  ),
                  StatusChip(t.status),
                ],
              ),
              const SizedBox(height: 12),
              if (t.canPayNow) ...[
                PayNowBox(order: t.asPlaced, failed: t.paymentState == 'FAILED'),
                const SizedBox(height: 12),
              ],
              OrderCard(
                title: 'Progress',
                child: OrderTimeline(status: t.status, reached: t.reached),
              ),
              if ((t.courierName ?? '').isNotEmpty || (t.trackingNumber ?? '').isNotEmpty)
                OrderCard(
                  title: 'Courier',
                  child: Text(
                    [t.courierName, t.trackingNumber].whereType<String>().where((s) => s.isNotEmpty).join(' · '),
                  ),
                ),
              if (t.lines.isNotEmpty)
                OrderCard(
                  title: 'Items',
                  child: OrderLines(lines: t.lines),
                ),
              if (t.total != null)
                OrderCard(
                  title: 'Payment',
                  child: Column(
                    children: [
                      if (t.shipping != null) SumRow('Delivery', t.shipping == 0 ? 'Free' : taka(t.shipping!)),
                      SumRow('Total', taka(t.total!), bold: true),
                      const SizedBox(height: 6),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          paymentWords(
                                t.paymentMethod ?? 'CASH',
                                t.paymentState,
                                refunded: t.refunded,
                                total: t.total,
                              ) ??
                              '',
                          style: const TextStyle(color: AppColors.muted),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }
}
