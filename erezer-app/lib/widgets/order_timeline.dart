import 'package:material_ui/material_ui.dart';

import '../app/theme.dart';
import '../data/models.dart';

String shortDate(DateTime d) {
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  final h = d.hour % 12 == 0 ? 12 : d.hour % 12;
  final m = d.minute.toString().padLeft(2, '0');
  return '${d.day} ${months[d.month - 1]} ${d.year}, $h:$m ${d.hour < 12 ? 'am' : 'pm'}';
}

/// A coloured label for an order's status.
class StatusChip extends StatelessWidget {
  const StatusChip(this.status, {super.key});
  final String status;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = switch (status) {
      'DELIVERED' => (const Color(0xFFDCFCE7), const Color(0xFF166534)),
      'CANCELLED' || 'RETURNED' => (const Color(0xFFFEE2E2), const Color(0xFF991B1B)),
      'PLACED' || 'PENDING' => (AppColors.soft, AppColors.ink),
      _ => (const Color(0xFFDBEAFE), const Color(0xFF1E40AF)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
      child: Text(
        orderStatusLabel(status),
        style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w700),
      ),
    );
  }
}

/// The order's steps from Placed to Delivered, done ones in black with their dates.
class OrderTimeline extends StatelessWidget {
  const OrderTimeline({super.key, required this.status, required this.reached});
  final String status;
  final Map<String, DateTime> reached;

  @override
  Widget build(BuildContext context) {
    if (status == 'CANCELLED' || status == 'RETURNED') {
      final at = reached[status];
      return Row(
        children: [
          const Icon(Icons.cancel, color: Color(0xFFB91C1C)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              status == 'CANCELLED' ? 'This order was cancelled.' : 'This order has been returned.',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
          if (at != null) Text(shortDate(at), style: TextStyle(color: AppColors.muted, fontSize: 12)),
        ],
      );
    }
    final current = switch (status) {
      'PENDING' => 0,
      // "In production" comes after Accepted, before Processing.
      'IN_PRODUCTION' => orderSteps.indexOf('ACCEPTED'),
      _ => orderSteps.indexOf(status),
    };
    return Column(
      key: const Key('order-timeline'),
      children: [
        for (var i = 0; i < orderSteps.length; i++)
          _Step(
            label: orderStatusLabel(orderSteps[i]),
            done: i <= current,
            current: i == current,
            last: i == orderSteps.length - 1,
            at: reached[orderSteps[i]],
          ),
      ],
    );
  }
}

class _Step extends StatelessWidget {
  const _Step({required this.label, required this.done, required this.current, required this.last, this.at});
  final String label;
  final bool done;
  final bool current;
  final bool last;
  final DateTime? at;

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 24,
            child: Column(
              children: [
                Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: done ? AppColors.ink : AppColors.card,
                    border: Border.all(color: done ? AppColors.ink : AppColors.line, width: 2),
                  ),
                  child: done ? Icon(Icons.check, size: 13, color: AppColors.onInk) : null,
                ),
                if (!last) Expanded(child: Container(width: 2, color: done ? AppColors.ink : AppColors.line)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 18),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      label,
                      style: TextStyle(
                        fontWeight: current ? FontWeight.w700 : FontWeight.w500,
                        color: done ? AppColors.ink : AppColors.muted,
                      ),
                    ),
                  ),
                  if (at != null) Text(shortDate(at!), style: TextStyle(color: AppColors.muted, fontSize: 12)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
