import 'dart:convert';

import 'package:erezer_app/data/api_client.dart';
import 'package:erezer_app/data/cart.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final sent = <String, Map<String, dynamic>>{};
  final headers = <String, Map<String, String>>{};
  final api = ApiClient(client: MockClient((req) async {
    sent[req.url.path] = jsonDecode(req.body) as Map<String, dynamic>;
    headers[req.url.path] = req.headers;
    return http.Response(jsonEncode({'id': 'o1', 'orderNumber': 'EZ-1'}), 201,
        headers: {'content-type': 'application/json'});
  }));
  const line = CartLine(productId: 1, quantity: 1, name: 'Tee', unitPrice: 500, variantId: 10);

  test("an order carries the phone that placed it, so its updates reach that phone", () async {
    await api.placeGuestOrder(
      lines: const [line],
      firstName: 'A',
      lastName: 'B',
      email: 'a@b.com',
      phone: '017',
      address: 'X',
      paymentMethod: 'CASH',
      pushToken: 'phone-token',
    );
    expect(sent['/app/consumer/guest/orders']?['pushToken'], 'phone-token');

    await api.placeGuestOrder(
      lines: const [line],
      firstName: 'A',
      lastName: 'B',
      email: 'a@b.com',
      phone: '017',
      address: 'X',
      paymentMethod: 'CASH',
    );
    // No notifications on this phone: nothing is sent.
    expect(sent['/app/consumer/guest/orders']!.containsKey('pushToken'), isFalse);
  });

  test('the phone registers, joins the account and leaves it on logout', () async {
    await api.registerDevice('t1');
    expect(sent['/api/push/devices'], {'token': 't1'});
    expect(headers['/api/push/devices']?['Authorization'], isNull);

    await api.linkDevice('u1', 'access', 't1');
    expect(sent['/app/consumer/u1/push-devices'], {'token': 't1'});
    expect(headers['/app/consumer/u1/push-devices']?['Authorization'], 'Bearer access');

    await api.unlinkDevice('u1', 'access', 't1');
    expect(sent['/app/consumer/u1/push-devices/remove'], {'token': 't1'});
  });
}
