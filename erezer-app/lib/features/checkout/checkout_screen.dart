import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/auth.dart';
import '../../data/push.dart';
import '../account/account_screen.dart';
import '../../data/cart.dart';
import '../product/quick_add.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';
import '../../widgets/common.dart';

/// Delivery details, delivery area, promo code and payment, with the total
/// worked out by the server, then Place order. Orders are placed as a guest
/// for now; accounts come later.
class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

/// The ways to pay the app can take so far.
const _appPaysWith = {'CASH', 'BKASH'};

const _methodNames = {'CASH': 'Cash on delivery', 'BKASH': 'bKash', 'CARD': 'Card'};

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  final _form = GlobalKey<FormState>();
  final _first = TextEditingController();
  final _last = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _address = TextEditingController();
  final _code = TextEditingController();

  int? _zoneId;
  String? _appliedCode;
  String _method = 'CASH';
  CheckoutQuote? _quote;
  String? _quoteError;
  bool _quoting = false;
  bool _placing = false;
  String? _placeError;
  int _quoteRun = 0;

  @override
  void initState() {
    super.initState();
    _prefill();
  }

  /// Fills in the customer's details from last time, and picks their delivery
  /// area from last time (if the shop still has it), else the shop's default.
  Future<void> _prefill() async {
    final saved = SavedDetails.load();
    List<ShippingZone> zones;
    try {
      zones = await ref.read(shippingZonesProvider.future);
    } catch (_) {
      zones = const [];
    }
    final d = await saved;
    if (!mounted) return;
    // Fill only what the customer hasn't started typing.
    void fill(TextEditingController c, String value) {
      if (c.text.isEmpty && value.isNotEmpty) c.text = value;
    }

    fill(_first, d.firstName);
    fill(_last, d.lastName);
    fill(_email, d.email);
    fill(_phone, d.phone);
    fill(_address, d.address);
    if (_zoneId != null) return;
    final zone =
        zones.where((z) => z.id == d.zoneId).firstOrNull ??
        zones.where((z) => z.isDefault).firstOrNull ??
        zones.firstOrNull;
    if (zone != null) {
      _pickZone(zone.id);
    } else {
      // Without zones the server prices delivery itself.
      _refreshQuote();
    }
  }

  @override
  void dispose() {
    for (final c in [_first, _last, _email, _phone, _address, _code]) {
      c.dispose();
    }
    super.dispose();
  }

  void _pickZone(int id) {
    setState(() => _zoneId = id);
    _refreshQuote();
  }

  Future<void> _refreshQuote() async {
    final lines = ref.read(cartProvider);
    if (lines.isEmpty) return;
    final run = ++_quoteRun;
    setState(() {
      _quoting = true;
      _quoteError = null;
    });
    try {
      final q = await ref.read(apiProvider).quote(lines, zoneId: _zoneId, couponCode: _appliedCode);
      if (!mounted || run != _quoteRun) return;
      setState(() => _quote = q);
      if (_appliedCode != null && !q.couponApplied) {
        _say(q.couponMessage ?? 'This code can’t be used on this order.');
        setState(() => _appliedCode = null);
      }
    } on ApiException catch (e) {
      if (mounted && run == _quoteRun) setState(() => _quoteError = e.message);
    } finally {
      if (mounted && run == _quoteRun) setState(() => _quoting = false);
    }
  }

  void _say(String text) => ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text)));

  List<String> _methods(StoreSettings? settings) =>
      _quote?.allowedPaymentMethods ?? settings?.paymentMethods ?? const ['CASH'];

  Future<void> _place(List<String> methods) async {
    setState(() => _placeError = null);
    // Problems show on the page, not in a pop-up that would sit over this button.
    if (!_form.currentState!.validate()) {
      setState(() => _placeError = 'Please fill in the details marked in red.');
      return;
    }
    if (!methods.contains(_method) || !_appPaysWith.contains(_method)) {
      setState(() => _placeError = 'Please choose a way to pay.');
      return;
    }
    if (ref.read(linesNeedingSizeProvider).isNotEmpty) {
      setState(() => _placeError = 'Please choose a size for each item in your order summary.');
      return;
    }
    final lines = ref.read(cartProvider);
    final session = ref.read(authProvider);
    if (session != null && !session.emailVerified) {
      setState(() => _placeError = 'Please confirm your email first (see above), or log out to order as a guest.');
      return;
    }
    setState(() => _placing = true);
    try {
      // Logged in: the order goes to the account and shows in My orders.
      final order = session != null
          ? await ref
                .read(authProvider.notifier)
                .call(
                  (api, s) => api.placeOrder(
                    userId: s.userId,
                    token: s.accessToken,
                    lines: lines,
                    phone: _phone.text,
                    address: _address.text,
                    paymentMethod: _method,
                    zoneId: _zoneId,
                    couponCode: _appliedCode,
                    pushToken: ref.read(pushProvider).token,
                  ),
                )
          : await ref
                .read(apiProvider)
                .placeGuestOrder(
                  lines: lines,
                  firstName: _first.text,
                  lastName: _last.text,
                  email: _email.text,
                  phone: _phone.text,
                  address: _address.text,
                  paymentMethod: _method,
                  zoneId: _zoneId,
                  couponCode: _appliedCode,
                  pushToken: ref.read(pushProvider).token,
                );
      await SavedDetails(
        firstName: _first.text.trim(),
        lastName: _last.text.trim(),
        email: _email.text.trim(),
        phone: _phone.text.trim(),
        address: _address.text.trim(),
        zoneId: _zoneId,
      ).save();
      ref.read(cartProvider.notifier).clear();
      if (!mounted) return;
      // The Cart tab goes back to the (now empty) cart; the next page opens over it.
      // A bKash order is saved first, then paid on bKash's page.
      final router = GoRouter.of(context)..go('/cart');
      router.push(_method == 'BKASH' ? '/order/bkash' : '/order/placed', extra: order);
    } on ApiException catch (e) {
      if (mounted) setState(() => _placeError = e.message);
      // Stock or prices may have changed: show the latest.
      _refreshQuote();
    } finally {
      if (mounted) setState(() => _placing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final lines = ref.watch(cartProvider);
    // A size picked (or changed) here: ask the server for the new total.
    ref.listen(cartProvider, (before, now) {
      if (now.isNotEmpty && !_placing) _refreshQuote();
    });
    final sizesMissing = ref.watch(linesNeedingSizeProvider).isNotEmpty;
    final session = ref.watch(authProvider);
    final zones = ref.watch(shippingZonesProvider).value ?? const [];
    final settings = ref.watch(storeSettingsProvider).value;
    final methods = _methods(settings);
    final usable = methods.where(_appPaysWith.contains).toList();
    if (usable.isNotEmpty && !usable.contains(_method)) _method = usable.first;
    final q = _quote;

    if (lines.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('Checkout')),
        body: const EmptyState(
          icon: Icons.shopping_bag_outlined,
          title: 'Your cart is empty',
          text: 'Add something first.',
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Checkout', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const _Heading('Delivery details'),
            // Logged in: the name and email come from the account.
            if (session != null) ...[
              Text(
                'Ordering as ${session.name.isEmpty ? session.email : '${session.name} (${session.email})'}',
                key: const Key('ordering-as'),
                style: TextStyle(color: AppColors.muted),
              ),
              const SizedBox(height: 12),
              if (!session.emailVerified) ...[const VerifyEmailNotice(), const SizedBox(height: 12)],
            ] else
              Row(
                children: [
                  Expanded(
                    child: _Field(
                      _first,
                      'First name',
                      key: const Key('first-name'),
                      autofill: AutofillHints.givenName,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _Field(_last, 'Last name', key: const Key('last-name'), autofill: AutofillHints.familyName),
                  ),
                ],
              ),
            _Field(
              _phone,
              'Mobile number',
              key: const Key('phone'),
              keyboard: TextInputType.phone,
              autofill: AutofillHints.telephoneNumber,
              hint: '01XXXXXXXXX',
              validator: (v) =>
                  RegExp(r'^[0-9+\-\s()]{7,20}$').hasMatch(v.trim()) ? null : 'Enter a valid mobile number',
            ),
            if (session == null)
              _Field(
                _email,
                'Email',
                key: const Key('email'),
                keyboard: TextInputType.emailAddress,
                autofill: AutofillHints.email,
                validator: (v) =>
                    RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v.trim()) ? null : 'Enter a valid email',
              ),
            _Field(
              _address,
              'Full address',
              key: const Key('address'),
              hint: 'House, road, area, city',
              lines: 3,
              autofill: AutofillHints.fullStreetAddress,
              validator: (v) => v.trim().length > 255 ? 'Please keep the address under 255 letters' : null,
            ),
            if (zones.isNotEmpty) ...[
              const SizedBox(height: 8),
              const _Heading('Delivery area'),
              for (final z in zones)
                _RadioCard(
                  key: Key('zone-${z.id}'),
                  selected: _zoneId == z.id,
                  title: z.name,
                  trailing: taka(z.fee),
                  onTap: () => _pickZone(z.id),
                ),
            ],
            if (settings?.couponsEnabled ?? false) ...[
              const SizedBox(height: 8),
              const _Heading('Promo code'),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const Key('promo-code'),
                      controller: _code,
                      textCapitalization: TextCapitalization.characters,
                      decoration: _decoration('Promo code').copyWith(
                        suffixIcon: _appliedCode == null
                            ? null
                            : const Icon(Icons.check_circle, color: Color(0xFF15803D)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  OutlinedButton(
                    onPressed: () {
                      final code = _code.text.trim();
                      setState(() => _appliedCode = _appliedCode == null && code.isNotEmpty ? code : null);
                      if (_appliedCode == null) _code.clear();
                      _refreshQuote();
                    },
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(0, 52),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(_appliedCode == null ? 'Apply' : 'Remove'),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 16),
            const _Heading('Payment'),
            if (q?.paymentMessage != null)
              _Notice(q!.paymentMessage!, key: const Key('payment-conflict'))
            else ...[
              for (final m in methods)
                _RadioCard(
                  key: Key('pay-$m'),
                  selected: _method == m && _appPaysWith.contains(m),
                  enabled: _appPaysWith.contains(m),
                  title: _methodNames[m] ?? m,
                  subtitle: _appPaysWith.contains(m)
                      ? switch (m) {
                          'CASH' => 'Pay when your order arrives',
                          'BKASH' => 'Pay now from your bKash account',
                          _ => null,
                        }
                      : 'Coming to the app soon. Use the website for now.',
                  onTap: () => setState(() => _method = m),
                ),
              if (usable.isEmpty)
                const _Notice(
                  'These items can only be paid for by card, which is coming to the app soon. '
                  'You can order them on erezer.com for now.',
                ),
            ],
            const SizedBox(height: 16),
            const _Heading('Order summary'),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    for (final l in lines)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${l.quantity} × ${l.name}${l.label.isEmpty ? '' : ' (${l.label})'}',
                                    style: TextStyle(color: AppColors.muted),
                                  ),
                                  CartSizePicker(key: ValueKey('picker-${l.key}'), line: l, removable: true),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    const Divider(height: 20),
                    if (q == null)
                      _quoteError != null
                          ? Column(
                              children: [
                                Text(_quoteError!, style: const TextStyle(color: Color(0xFFB91C1C))),
                                TextButton(onPressed: _refreshQuote, child: const Text('Try again')),
                              ],
                            )
                          : Padding(
                              padding: EdgeInsets.all(8),
                              child: CircularProgressIndicator(color: AppColors.ink),
                            )
                    else ...[
                      _SumRow('Subtotal', taka(q.subtotal)),
                      if (q.customSurcharge > 0) _SumRow('Custom size', taka(q.customSurcharge)),
                      if (q.discount > 0) _SumRow('Discount', '−${taka(q.discount)}'),
                      _SumRow('Delivery', q.shippingFee == 0 ? 'Free' : taka(q.shippingFee)),
                      if (q.freeShippingNote != null)
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            q.freeShippingNote!,
                            style: const TextStyle(color: Color(0xFF15803D), fontSize: 13),
                          ),
                        ),
                      const Divider(height: 20),
                      _SumRow('Total', taka(q.total), bold: true, valueKey: const Key('quote-total')),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          decoration: BoxDecoration(
            color: AppColors.card,
            border: Border(top: BorderSide(color: AppColors.line)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (sizesMissing) ...[
                const _Notice('Choose a size for each item in your order summary to place the order.'),
                const SizedBox(height: 8),
              ],
              if (_placeError != null) ...[
                _Notice(_placeError!, key: const Key('place-error')),
                const SizedBox(height: 8),
              ],
              SizedBox(
                height: 52,
                width: double.infinity,
                child: FilledButton(
                  key: const Key('place-order'),
                  onPressed: _placing || _quoting || q == null || usable.isEmpty || q.paymentMessage != null
                      ? null
                      : () => _place(methods),
                  child: _placing
                      ? SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(color: AppColors.onInk, strokeWidth: 2),
                        )
                      : Text(
                          '${_method == 'BKASH' ? 'Pay with bKash' : 'Place order'}'
                          '${q == null ? '' : ' · ${taka(q.total)}'}',
                          style: const TextStyle(fontSize: 16),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

InputDecoration _decoration(String label, {String? hint}) => InputDecoration(
  labelText: label,
  hintText: hint,
  filled: true,
  fillColor: AppColors.card,
  border: OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: BorderSide(color: AppColors.line),
  ),
  enabledBorder: OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: BorderSide(color: AppColors.line),
  ),
  focusedBorder: OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: BorderSide(color: AppColors.ink),
  ),
);

class _Field extends StatelessWidget {
  const _Field(
    this.controller,
    this.label, {
    super.key,
    this.hint,
    this.keyboard,
    this.lines = 1,
    this.autofill,
    this.validator,
  });
  final TextEditingController controller;
  final String label;
  final String? hint;
  final TextInputType? keyboard;
  final int lines;
  final String? autofill;
  final String? Function(String value)? validator;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: controller,
        keyboardType: lines > 1 ? TextInputType.streetAddress : keyboard,
        minLines: lines,
        maxLines: lines,
        textCapitalization: keyboard == null ? TextCapitalization.words : TextCapitalization.none,
        autofillHints: autofill == null ? null : [autofill!],
        decoration: _decoration(label, hint: hint),
        validator: (v) {
          final value = v ?? '';
          if (value.trim().isEmpty) return 'Please fill this in';
          return validator?.call(value);
        },
      ),
    );
  }
}

class _Heading extends StatelessWidget {
  const _Heading(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Text(text, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
  );
}

class _RadioCard extends StatelessWidget {
  const _RadioCard({
    super.key,
    required this.selected,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.trailing,
    this.enabled = true,
  });
  final bool selected;
  final bool enabled;
  final String title;
  final String? subtitle;
  final String? trailing;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: AppColors.card,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: selected ? AppColors.ink : AppColors.line, width: selected ? 1.6 : 1),
          ),
          child: Opacity(
            opacity: enabled ? 1 : 0.5,
            child: Row(
              children: [
                Icon(
                  selected ? Icons.radio_button_checked : Icons.radio_button_off,
                  color: selected ? AppColors.ink : AppColors.muted,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
                      if (subtitle != null) Text(subtitle!, style: TextStyle(color: AppColors.muted, fontSize: 13)),
                    ],
                  ),
                ),
                if (trailing != null) Text(trailing!, style: const TextStyle(fontWeight: FontWeight.w600)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SumRow extends StatelessWidget {
  const _SumRow(this.label, this.value, {this.bold = false, this.valueKey});
  final String label;
  final String value;
  final bool bold;
  final Key? valueKey;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontSize: bold ? 17 : 15, fontWeight: bold ? FontWeight.w700 : FontWeight.w400);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Expanded(child: Text(label, style: style)),
          Text(value, key: valueKey, style: style),
        ],
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: const Color(0xFFFEF2F2),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: const Color(0xFFFECACA)),
    ),
    child: Text(text, style: const TextStyle(color: Color(0xFF991B1B))),
  );
}
