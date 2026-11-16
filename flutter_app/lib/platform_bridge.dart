import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:file_selector/file_selector.dart';

const nativeBridge = MethodChannel('com.led.phone/bridge');
final bridge = BusPlatform();
bool get usesAndroid =>
    !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

class BusPlatform {
  SharedPreferences? _preferences;
  AudioPlayer? _player;
  Completer<bool>? _completion;
  Future<SharedPreferences> get preferences async =>
      _preferences ??= await SharedPreferences.getInstance();
  void setMethodCallHandler(Future<dynamic> Function(MethodCall)? handler) {
    if (usesAndroid) nativeBridge.setMethodCallHandler(handler);
  }

  Future<Map<K, V>?> invokeMapMethod<K, V>(
    String method, [
    dynamic args,
  ]) async {
    final value = await invokeMethod<dynamic>(method, args);
    return value == null ? null : Map<K, V>.from(value as Map);
  }

  Future<T?> invokeMethod<T>(String method, [dynamic args]) async {
    if (usesAndroid) return nativeBridge.invokeMethod<T>(method, args);
    dynamic value;
    switch (method) {
      case 'asset':
        try {
          value = (await rootBundle.load('assets/$args')).buffer.asUint8List();
        } catch (_) {
          value = null;
        }
      case 'init':
        final p = await preferences;
        value = <String, dynamic>{
          'preferences': {for (final key in p.getKeys()) key: p.get(key)},
          'accent': 0xffd0bcff,
          'index': p.getInt('lastIndex') ?? 0,
        };
        for (final name in ['routes', 'catalog', 'audio', 'external']) {
          value[name] = await rootBundle.loadString('assets/phone/$name.json');
        }
      case 'snapshot':
        final p = await preferences;
        value = {'accent': 0xffd0bcff, 'index': p.getInt('lastIndex') ?? 0};
      case 'save':
        await save(args['key'], args['value']);
      case 'state':
        await save('lastRoute', args['route']);
        await save('lastDirection', args['direction']);
        await save('lastIndex', args['index']);
      case 'accent':
        value = 0xffd0bcff;
      case 'play':
        value = await play(args as String);
      case 'stop':
        await stop();
      case 'background':
        final s = (await preferences).getString('backgroundBytes');
        value = s == null ? null : base64Decode(s);
      case 'pickBackground':
        const types = XTypeGroup(
          label: '圖片',
          extensions: ['png', 'jpg', 'jpeg', 'webp'],
          uniformTypeIdentifiers: ['public.image'],
        );
        final file = await openFile(acceptedTypeGroups: [types]);
        if (file != null) {
          final bytes = await file.readAsBytes();
          if (bytes.length > 3 * 1024 * 1024) {
            throw PlatformException(code: 'photo', message: '請選擇小於 3 MB 的圖片');
          }
          await save('backgroundBytes', base64Encode(bytes));
          await save('customBackground', true);
          value = bytes;
        }
      case 'bellState':
      case 'keepScreen':
        break;
      default:
        throw MissingPluginException('Unsupported method $method');
    }
    return value as T?;
  }

  Future<void> save(String key, dynamic value) async {
    final p = await preferences;
    if (value is bool) {
      await p.setBool(key, value);
    } else if (value is int) {
      await p.setInt(key, value);
    } else if (value is String) {
      await p.setString(key, value);
    }
  }

  Future<bool> play(String path) async {
    await stop();
    final player = _player ??= AudioPlayer();
    final done = Completer<bool>();
    _completion = done;
    final subscription = player.onPlayerComplete.listen((_) {
      if (!done.isCompleted) done.complete(true);
    });
    try {
      await player.play(AssetSource(path));
      return await done.future.timeout(
        const Duration(minutes: 10),
        onTimeout: () => false,
      );
    } catch (_) {
      return false;
    } finally {
      await subscription.cancel();
      if (identical(_completion, done)) _completion = null;
    }
  }

  Future<void> stop() async {
    final done = _completion;
    _completion = null;
    if (done != null && !done.isCompleted) done.complete(false);
    await _player?.stop();
  }
}
