import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:acs_nfc_reader/acs_nfc_reader.dart';

void main() {
  const MethodChannel channel = MethodChannel('acs_nfc_reader');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await AcsNfcReader.platformVersion, '42');
  });
}
