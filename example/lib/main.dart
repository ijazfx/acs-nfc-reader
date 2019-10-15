import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:acs_nfc_reader/acs_nfc_reader.dart';
import 'package:hex/hex.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _cardData = 'Unknown';

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    try {
      bool status = await AcsNfcReader.initialize();
      if (status) {
        setState(() {
          _cardData = "Initialized";
        });
        AcsNfcReader.onScan((MethodCall call) {
          var data = HEX.decode(call.arguments);
          var cardData = String.fromCharCodes(data);
          setState(() {
            _cardData = "$cardData";
          });
          return;
        });
      }
    } on PlatformException {
      _cardData = 'Error';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _cardData = "SCAN YOUR CARD";
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('WAPP v1.0'),
        ),
        body: Center(
          child: Text('$_cardData\n'),
        ),
      ),
    );
  }
}
