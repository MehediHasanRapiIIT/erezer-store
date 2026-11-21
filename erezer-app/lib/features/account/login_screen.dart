import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/auth.dart';

/// Log in, or create an account, with email and password: the same accounts
/// as the website.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.signUp = false});
  final bool signUp;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _first = TextEditingController();
  final _last = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  late bool _signUp = widget.signUp;
  bool _busy = false;
  bool _showPassword = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_first, _last, _email, _password]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (!_form.currentState!.validate()) return;
    setState(() => _busy = true);
    final auth = ref.read(authProvider.notifier);
    try {
      if (_signUp) {
        await auth.register(firstName: _first.text, lastName: _last.text, email: _email.text, password: _password.text);
      } else {
        await auth.login(_email.text, _password.text);
      }
      if (mounted) Navigator.of(context).pop();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _forgot() async {
    final email = TextEditingController(text: _email.text);
    final send = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Forgot your password?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('We will email you a link to set a new one.'),
            const SizedBox(height: 12),
            TextField(
              controller: email,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: 'Email'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Send link')),
        ],
      ),
    );
    if (send != true || email.text.trim().isEmpty || !mounted) return;
    try {
      final note = await ref.read(apiProvider).forgotPassword(email.text);
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(note)));
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  InputDecoration _field(String label, {Widget? suffix}) => InputDecoration(
    labelText: label,
    filled: true,
    fillColor: AppColors.card,
    suffixIcon: suffix,
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: AppColors.line),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: AppColors.ink),
    ),
  );

  String? _required(String? v) => (v ?? '').trim().isEmpty ? 'Please fill this in' : null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_signUp ? 'Create account' : 'Log in', style: const TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: Form(
        key: _form,
        child: AutofillGroup(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Image.asset('assets/images/wordmark.png', height: 18),
              const SizedBox(height: 8),
              Text(
                _signUp ? 'Create your account to see your orders in one place.' : 'Welcome back.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.muted),
              ),
              const SizedBox(height: 24),
              if (_signUp) ...[
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        key: const Key('signup-first'),
                        controller: _first,
                        textCapitalization: TextCapitalization.words,
                        autofillHints: const [AutofillHints.givenName],
                        decoration: _field('First name'),
                        validator: _required,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextFormField(
                        key: const Key('signup-last'),
                        controller: _last,
                        textCapitalization: TextCapitalization.words,
                        autofillHints: const [AutofillHints.familyName],
                        decoration: _field('Last name'),
                        validator: _required,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
              ],
              TextFormField(
                key: const Key('login-email'),
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
                decoration: _field('Email'),
                validator: (v) =>
                    RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch((v ?? '').trim()) ? null : 'Enter a valid email',
              ),
              const SizedBox(height: 12),
              TextFormField(
                key: const Key('login-password'),
                controller: _password,
                obscureText: !_showPassword,
                autofillHints: [_signUp ? AutofillHints.newPassword : AutofillHints.password],
                decoration: _field(
                  'Password',
                  suffix: IconButton(
                    icon: Icon(_showPassword ? Icons.visibility_off : Icons.visibility),
                    tooltip: _showPassword ? 'Hide password' : 'Show password',
                    onPressed: () => setState(() => _showPassword = !_showPassword),
                  ),
                ),
                validator: (v) {
                  if ((v ?? '').isEmpty) return 'Please fill this in';
                  if (_signUp && v!.length < 8) return 'Use at least 8 characters';
                  return null;
                },
                onFieldSubmitted: (_) => _submit(),
              ),
              if (!_signUp)
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: _forgot,
                    style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                    child: const Text('Forgot password?'),
                  ),
                ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Container(
                  key: const Key('login-error'),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFEF2F2),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFFECACA)),
                  ),
                  child: Text(_error!, style: const TextStyle(color: Color(0xFF991B1B))),
                ),
              ],
              const SizedBox(height: 16),
              SizedBox(
                height: 52,
                child: FilledButton(
                  key: const Key('login-submit'),
                  onPressed: _busy ? null : _submit,
                  child: _busy
                      ? SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(color: AppColors.onInk, strokeWidth: 2),
                        )
                      : Text(_signUp ? 'Create account' : 'Log in', style: const TextStyle(fontSize: 16)),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                key: const Key('switch-mode'),
                onPressed: () => setState(() {
                  _signUp = !_signUp;
                  _error = null;
                }),
                style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                child: Text(_signUp ? 'Already have an account? Log in' : 'New here? Create an account'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
