import 'package:erezer_app/data/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test("bKash's return trip goes to the shop's server, which says how it went", () async {
    Uri? asked;
    final api = ApiClient(
      client: MockClient((req) async {
        asked = req.url;
        // The server records the payment and points a website customer at its result page.
        return http.Response(
          '',
          302,
          headers: {'location': 'https://erezer.com/bkash-callback?paymentId=TR001&status=failure&orderId=abc'},
        );
      }),
    );
    final status = await api.bkashReturn(
      Uri.parse('http://localhost:8080/api/payments/bkash/callback?paymentID=TR001&status=success'),
    );
    expect(asked?.path, '/api/payments/bkash/callback');
    expect(asked?.queryParameters['paymentID'], 'TR001');
    // The server's verdict wins over what the address said.
    expect(status, 'failure');
  });

  test('with no answer from the server, bKash\'s own word is used', () async {
    final api = ApiClient(client: MockClient((_) async => throw Exception('offline')));
    final status = await api.bkashReturn(Uri.parse('https://x/api/payments/bkash/callback?paymentID=T&status=cancel'));
    expect(status, 'cancel');
  });
}
