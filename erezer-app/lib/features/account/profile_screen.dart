import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/auth.dart';
import '../../data/models.dart';
import '../../widgets/common.dart';

/// The customer's name (which they can change) and email.
class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  final _form = GlobalKey<FormState>();
  final _first = TextEditingController();
  final _last = TextEditingController();
  Profile? _profile;
  String? _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _first.dispose();
    _last.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final p = await ref.read(authProvider.notifier).call((api, s) => api.profile(s.userId, s.accessToken));
      if (!mounted) return;
      _first.text = p.firstName;
      _last.text = p.lastName;
      setState(() => _profile = p);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final p = Profile(firstName: _first.text, lastName: _last.text, email: _profile!.email, phone: _profile!.phone);
      final saved = await ref
          .read(authProvider.notifier)
          .call((api, s) => api.updateProfile(s.userId, s.accessToken, p));
      if (!mounted) return;
      setState(() => _profile = saved);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  InputDecoration _field(String label) => InputDecoration(
    labelText: label,
    filled: true,
    fillColor: Colors.white,
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: AppColors.line),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final p = _profile;
    return Scaffold(
      appBar: AppBar(
        title: const Text('My details', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: p == null
          ? (_error != null
                ? ErrorRetry(message: _error!, onRetry: _load)
                : const Center(child: CircularProgressIndicator(color: AppColors.ink)))
          : Form(
              key: _form,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  TextFormField(
                    key: const Key('profile-first'),
                    controller: _first,
                    textCapitalization: TextCapitalization.words,
                    decoration: _field('First name'),
                    validator: (v) => (v ?? '').trim().isEmpty ? 'Please fill this in' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    key: const Key('profile-last'),
                    controller: _last,
                    textCapitalization: TextCapitalization.words,
                    decoration: _field('Last name'),
                    validator: (v) => (v ?? '').trim().isEmpty ? 'Please fill this in' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(initialValue: p.email, enabled: false, decoration: _field('Email')),
                  const SizedBox(height: 20),
                  SizedBox(
                    height: 52,
                    child: FilledButton(
                      key: const Key('profile-save'),
                      onPressed: _saving ? null : _save,
                      child: const Text('Save'),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
