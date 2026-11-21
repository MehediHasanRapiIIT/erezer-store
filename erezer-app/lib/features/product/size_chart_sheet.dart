import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/models.dart';

/// Opens the size chart from the bottom of the screen, in inches or cm.
Future<void> showSizeChart(BuildContext context, SizeChart chart, String? fitLabel) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    backgroundColor: Colors.white,
    builder: (_) => _SizeChartSheet(chart: chart, fitLabel: fitLabel),
  );
}

class _SizeChartSheet extends StatefulWidget {
  const _SizeChartSheet({required this.chart, this.fitLabel});
  final SizeChart chart;
  final String? fitLabel;

  @override
  State<_SizeChartSheet> createState() => _SizeChartSheetState();
}

class _SizeChartSheetState extends State<_SizeChartSheet> {
  bool inches = true;

  String _cell(({double? cm, double? inch}) c) {
    final v = inches ? c.inch : c.cm;
    if (v == null) return '–';
    return v == v.roundToDouble() ? v.round().toString() : v.toStringAsFixed(1);
  }

  @override
  Widget build(BuildContext context) {
    final chart = widget.chart;
    const head = TextStyle(fontWeight: FontWeight.w700);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          key: const Key('size-chart'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    widget.fitLabel == null ? 'Size chart' : 'Size chart · ${widget.fitLabel}',
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                ),
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment(value: true, label: Text('in')),
                    ButtonSegment(value: false, label: Text('cm')),
                  ],
                  selected: {inches},
                  showSelectedIcon: false,
                  onSelectionChanged: (s) => setState(() => inches = s.first),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Table(
              border: TableBorder.symmetric(inside: const BorderSide(color: AppColors.line)),
              children: [
                TableRow(
                  decoration: const BoxDecoration(color: Color(0xFFF5F5F5)),
                  children: [
                    const _Cell('Size', style: head),
                    for (final c in chart.columns) _Cell(c, style: head),
                  ],
                ),
                for (final r in chart.rows)
                  TableRow(
                    children: [
                      _Cell(r.size, style: head),
                      for (var i = 0; i < chart.columns.length; i++)
                        _Cell(i < r.cells.length ? _cell(r.cells[i]) : '–'),
                    ],
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Cell extends StatelessWidget {
  const _Cell(this.text, {this.style});
  final String text;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
    child: Text(text, textAlign: TextAlign.center, style: style),
  );
}
