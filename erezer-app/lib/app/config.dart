/// Where the app finds the Erezer server. The live server is the default; a
/// test build can point elsewhere, e.g. the backend on this PC:
///   flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'https://api.erezer.com');

/// The shop's website, for the design studio the app opens inside itself.
///   flutter run --dart-define=STORE_URL=http://10.0.2.2:4200
const storeUrl = String.fromEnvironment('STORE_URL', defaultValue: 'https://erezer.com');
