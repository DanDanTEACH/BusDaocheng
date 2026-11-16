import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:bus_daocheng/platform_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'portable backend loads bundled routes and persists station state',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      SharedPreferences.setMockInitialValues({});
      final platform = BusPlatform();
      final data = await platform.invokeMapMethod<String, dynamic>('init');
      expect((jsonDecode(data!['routes']) as List).length, 45);
      final image = await platform.invokeMethod<Uint8List>(
        'asset',
        'phone/maps/3020A_1.png',
      );
      expect(image, isNotEmpty);
      await platform.invokeMethod('state', {
        'route': '3020A',
        'direction': 1,
        'index': 4,
      });
      await platform.invokeMethod('save', {
        'key': 'autoDelaySeconds',
        'value': 7,
      });
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getInt('lastDirection'), 1);
      expect(prefs.getInt('lastIndex'), 4);
      expect(prefs.getInt('autoDelaySeconds'), 7);
    },
  );
}
