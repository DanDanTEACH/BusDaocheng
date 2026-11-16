import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:bus_daocheng/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final route = {
    'id': '302',
    'label': '302',
    'mapId': '3020A',
    'directions': {
      '0': {
        'to': 'C',
        'stations': ['A', 'B', 'C'],
      },
      '1': {
        'to': 'A',
        'stations': ['C', 'B', 'A'],
      },
    },
  };
  Future<BusAppState> setup(
    WidgetTester tester, {
    Map<String, String> audio = const {},
    Completer<bool>? completion,
  }) async {
    assetCache.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(nativeBridge, (call) async {
          if (call.method == 'init') {
            return {
              'routes': jsonEncode([route]),
              'catalog': '[]',
              'audio': jsonEncode(audio),
              'external': '{}',
              'preferences': {'autoDelaySeconds': 0},
              'index': 0,
              'accent': 0xffd0bcff,
            };
          }
          if (call.method == 'play') return completion?.future ?? true;
          return null;
        });
    await tester.pumpWidget(const BusApp());
    await tester.pumpAndSettle();
    return tester.state<BusAppState>(find.byType(BusApp));
  }

  testWidgets('settings navigation and playback hide bottom navigation', (
    tester,
  ) async {
    await setup(tester);
    await tester.tap(find.text('設定').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('播放顯示'));
    await tester.pumpAndSettle();
    expect(find.text('播放時顯示時鐘'), findsOneWidget);
    await tester.tap(find.text('播放').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('確認'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.text('下一站'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('audio completion locks station controls until finished', (
    tester,
  ) async {
    final completion = Completer<bool>();
    final state = await setup(
      tester,
      audio: {'B': 'b.mp3'},
      completion: completion,
    );
    final next = state.move(1);
    await tester.pump();
    expect(state.index, 1);
    expect(state.speaking, true);
    await state.move(1);
    expect(state.index, 1);
    completion.complete(true);
    await next;
    await tester.pump();
    expect(state.speaking, false);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('automatic playback skips missing files and stops at terminal', (
    tester,
  ) async {
    final state = await setup(tester);
    state.automatic = true;
    await state.announce();
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 1));
    }
    expect(state.index, 2);
    expect(state.automatic, false);
    await tester.pumpWidget(const SizedBox());
  });
}
