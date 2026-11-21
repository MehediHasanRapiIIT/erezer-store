import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/auth.dart';

/// The Account tab: log in or sign up, or (logged in) orders, profile and log out.
/// Tracking an order by its number works either way.
class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authProvider);
    final settings = ref.watch(storeSettingsProvider).value;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Account', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (session == null) ...[
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                gradient: const LinearGradient(colors: [Color(0xFF0A0A0A), Color(0xFF262626)]),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Your Erezer account',
                    style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'See your orders and pay for them in one place. Same account as erezer.com.',
                    style: TextStyle(color: Color(0xFFD4D4D4)),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton(
                          key: const Key('go-login'),
                          onPressed: () => context.push('/account/login'),
                          style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: AppColors.ink),
                          child: const Text('Log in'),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: OutlinedButton(
                          key: const Key('go-register'),
                          onPressed: () => context.push('/account/login?signup=1'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.white,
                            side: const BorderSide(color: Colors.white54),
                            shape: const StadiumBorder(),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                          ),
                          child: const Text('Create account'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ] else ...[
            Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: AppColors.ink,
                  child: Text(
                    (session.firstName.isNotEmpty ? session.firstName[0] : session.email[0]).toUpperCase(),
                    style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        session.name.isEmpty ? 'Hello!' : 'Hello, ${session.firstName}!',
                        key: const Key('account-hello'),
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                      ),
                      Text(session.email, style: const TextStyle(color: AppColors.muted)),
                    ],
                  ),
                ),
              ],
            ),
            if (!session.emailVerified) ...[const SizedBox(height: 16), const VerifyEmailNotice()],
          ],
          const SizedBox(height: 16),
          Card(
            child: Column(
              children: [
                if (session != null) ...[
                  _Item(
                    Icons.receipt_long_outlined,
                    'My orders',
                    key: const Key('my-orders'),
                    onTap: () => context.push('/account/orders'),
                  ),
                  const Divider(height: 1),
                ],
                _Item(
                  Icons.local_shipping_outlined,
                  'Track an order',
                  key: const Key('track-order'),
                  onTap: () => context.push('/account/track'),
                ),
                if (session != null) ...[
                  const Divider(height: 1),
                  _Item(
                    Icons.person_outline,
                    'My details',
                    key: const Key('my-details'),
                    onTap: () => context.push('/account/profile'),
                  ),
                ],
              ],
            ),
          ),
          if (settings?.supportPhone != null) ...[
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                leading: const Icon(Icons.support_agent),
                title: Text('Need help? ${settings!.supportPhone}'),
                subtitle: settings.supportHours == null ? null : Text(settings.supportHours!),
              ),
            ),
          ],
          if (session != null) ...[
            const SizedBox(height: 24),
            OutlinedButton.icon(
              key: const Key('logout'),
              onPressed: () => ref.read(authProvider.notifier).logout(),
              icon: const Icon(Icons.logout),
              label: const Text('Log out'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.ink,
                minimumSize: const Size.fromHeight(50),
                shape: const StadiumBorder(),
              ),
            ),
            const SizedBox(height: 8),
            TextButton(
              key: const Key('delete-account'),
              onPressed: () => context.push('/account/delete'),
              style: TextButton.styleFrom(foregroundColor: AppColors.muted),
              child: const Text('Delete my account'),
            ),
          ],
        ],
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item(this.icon, this.label, {super.key, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(icon),
    title: Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
    trailing: const Icon(Icons.chevron_right),
    onTap: onTap,
  );
}

/// "Please confirm your email", with a button to send the email again and one
/// to check after clicking the link. Ordering while logged in needs it.
class VerifyEmailNotice extends ConsumerStatefulWidget {
  const VerifyEmailNotice({super.key});

  @override
  ConsumerState<VerifyEmailNotice> createState() => _VerifyEmailNoticeState();
}

class _VerifyEmailNoticeState extends ConsumerState<VerifyEmailNotice> {
  bool _busy = false;
  String? _note;

  Future<void> _run(Future<String?> Function() job) async {
    setState(() {
      _busy = true;
      _note = null;
    });
    try {
      final note = await job();
      if (mounted) setState(() => _note = note);
    } on ApiException catch (e) {
      if (mounted) setState(() => _note = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(authProvider);
    if (session == null || session.emailVerified) return const SizedBox.shrink();
    return Container(
      key: const Key('verify-email'),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFDE68A)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Please confirm your email', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(
            'We sent a link to ${session.email}. Open it to confirm, then tap "I have confirmed". '
            'Check your spam folder if you can\'t find it.',
          ),
          if (_note != null) ...[
            const SizedBox(height: 6),
            Text(_note!, style: const TextStyle(color: AppColors.muted)),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              FilledButton(
                key: const Key('check-verified'),
                onPressed: _busy
                    ? null
                    : () => _run(() async {
                        final s = await ref.read(authProvider.notifier).refresh();
                        return s.emailVerified ? null : 'Not confirmed yet. Please open the link in the email.';
                      }),
                child: const Text('I have confirmed'),
              ),
              TextButton(
                onPressed: _busy ? null : () => _run(() => ref.read(apiProvider).resendVerification(session.email)),
                style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                child: const Text('Send the email again'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
