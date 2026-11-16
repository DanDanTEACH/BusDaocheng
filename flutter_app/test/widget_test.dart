import 'package:flutter_test/flutter_test.dart';
import 'package:bus_daocheng/main.dart';

void main() {
  test('route categories preserve trunk and commuter in other', () {
    expect(categoryOf('302'), 0);
    expect(categoryOf('小36'), 1);
    expect(categoryOf('市民小巴15'), 1);
    expect(categoryOf('紅35'), 2);
    expect(categoryOf('承德幹線'), 3);
    expect(categoryOf('內科通勤專車16'), 3);
  });
  test('route data preserves direction station order', () {
    final r = BusRoute({
      'id': '302',
      'label': '302',
      'mapId': '3020A',
      'directions': {
        '0': {
          'to': 'B',
          'stations': ['A', 'B'],
        },
        '1': {
          'to': 'A',
          'stations': ['B', 'A'],
        },
      },
    });
    expect(r.stations(0), ['A', 'B']);
    expect(r.stations(1), ['B', 'A']);
  });
}
