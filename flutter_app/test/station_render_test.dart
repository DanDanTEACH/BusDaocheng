import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:bus_daocheng/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('first station glyph is complete and original black-theme highlight is purple', () async {
    final route = BusRoute({
      'id': 'test',
      'label': 'Test',
      'mapId': 'test',
      'directions': {
        '0': {
          'to': 'end',
          'stations': ['A'],
        },
      },
    });
    final display = StationDisplay(
      stops: const ['A'],
      index: 0,
      route: route,
      way: 0,
      theme: 0,
      showClock: false,
      seconds: false,
    );
    final recording = ui.PictureRecorder();
    final canvas = Canvas(recording);
    StopsPainter(display, 0, 0).paint(canvas, const Size(1920, 560));
    final picture = recording.endRecording();
    final image = await picture.toImage(1920, 560);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
    final rows = <int>{};
    for (var y = 46; y < 125; y++) {
      for (var x = 775; x < 850; x++) {
        final i = (y * 1920 + x) * 4;
        if (bytes!.getUint8(i) == 189 &&
            bytes.getUint8(i + 1) == 98 &&
            bytes.getUint8(i + 2) == 157) {
          rows.add(y);
        }
      }
    }
    expect(
      rows.length,
      greaterThanOrEqualTo(40),
      reason:
          'A 42px glyph must not lose its first rows above the clipping edge.',
    );
    expect(rows.first, greaterThanOrEqualTo(46));
    image.dispose();
    picture.dispose();
  });
}
