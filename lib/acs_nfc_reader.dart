import 'dart:async';

import 'package:flutter/services.dart';

class AcsNfcReader {
  static const MethodChannel _channel = const MethodChannel('acs_nfc_reader');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> initialize() async {
    final bool status = await _channel.invokeMethod('initialize');
    return status;
  }

  static void onScan(Future<dynamic> handler(MethodCall call)) {
    _channel.setMethodCallHandler(handler);
  }
}
