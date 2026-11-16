import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

class BellModel extends CustomPainter {
  final int kind, state;
  BellModel(this.kind, this.state);
  void box(Canvas c, Rect r, double radius, List<Color> colors) {
    c.drawRRect(
      RRect.fromRectAndRadius(r, Radius.circular(radius)),
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ).createShader(r),
    );
  }

  void label(Canvas c, String text, Offset center, double size, Color color) {
    final p = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(
          fontSize: size,
          color: color,
          fontWeight: FontWeight.bold,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    p.paint(c, center - Offset(p.width / 2, p.height / 2));
  }

  void screw(Canvas c, double x, double y) {
    final r = Rect.fromCircle(center: Offset(x, y), radius: 8);
    box(c, r, 8, [const Color(0xffdfe4dc), const Color(0xff414a4b)]);
    c.drawLine(
      Offset(x - 4, y - 1),
      Offset(x + 4, y + 1),
      Paint()
        ..color = Colors.black54
        ..strokeWidth = 2,
    );
  }

  @override
  void paint(Canvas c, Size size) {
    final scale = math.min(size.width / 300, size.height / 400);
    c.save();
    c.translate(
      (size.width - 300 * scale) / 2,
      (size.height - 400 * scale) / 2,
    );
    c.scale(scale);
    if (kind == 0) {
      box(c, const Rect.fromLTWH(25, 75, 250, 250), 125, [
        const Color(0xff4b555e),
        const Color(0xff080d16),
      ]);
      c.save();
      c.clipPath(Path()..addOval(const Rect.fromLTWH(43, 93, 214, 214)));
      final red = state == 2, blue = state == 1;
      box(c, const Rect.fromLTWH(43, 93, 214, 107), 0, [
        red ? const Color(0xffffb2a3) : const Color(0xff702a35),
        red ? Colors.red : const Color(0xff29121c),
      ]);
      box(c, const Rect.fromLTWH(43, 200, 214, 107), 0, [
        blue ? const Color(0xff387bff) : const Color(0xff15164a),
        blue ? const Color(0xff092bd8) : const Color(0xff0d1135),
      ]);
      label(
        c,
        '♿',
        const Offset(150, 143),
        55,
        red ? Colors.white : const Color(0xff303445),
      );
      label(
        c,
        '乘 客 區',
        const Offset(150, 226),
        17,
        blue ? Colors.white : Colors.white54,
      );
      label(
        c,
        '下站停車',
        const Offset(150, 265),
        31,
        blue ? Colors.white : Colors.white60,
      );
      c.restore();
    } else if (kind == 1) {
      box(c, const Rect.fromLTWH(117, 65, 68, 300), 32, [
        Colors.grey.shade300,
        Colors.grey.shade900,
      ]);
      screw(c, 150, 81);
      screw(c, 150, 350);
      box(c, const Rect.fromLTWH(35, 86, 240, 250), 57, [
        const Color(0xffc3cbbf),
        const Color(0xff626c61),
        const Color(0xffadb6a6),
      ]);
      final random = math.Random(2241);
      for (var i = 0; i < 1300; i++) {
        final x = 50 + random.nextDouble() * 208,
            y = 102 + random.nextDouble() * 215;
        c.drawCircle(
          Offset(x, y),
          .6,
          Paint()..color = i.isEven ? Colors.white30 : Colors.black26,
        );
      }
      box(c, const Rect.fromLTWH(40, 88, 230, 120), 48, [
        const Color(0xff16203b),
        const Color(0xff030510),
      ]);
      c.drawRect(
        const Rect.fromLTWH(40, 155, 230, 53),
        Paint()..color = const Color(0xff080e20),
      );
      if (state == 1) {
        for (final p in [
          const Offset(100, 160),
          const Offset(140, 128),
          const Offset(191, 148),
          const Offset(162, 183),
        ]) {
          c.drawCircle(
            p,
            30,
            Paint()
              ..shader = RadialGradient(
                colors: [Colors.red.withValues(alpha: .8), Colors.transparent],
              ).createShader(Rect.fromCircle(center: p, radius: 30)),
          );
          c.drawCircle(p, 3, Paint()..color = const Color(0xffffe0c2));
        }
      }
      box(c, const Rect.fromLTWH(80, 235, 150, 76), 20, [
        const Color(0xff444d36),
        const Color(0xffe0e7b0),
      ]);
      box(c, const Rect.fromLTWH(87, 242, 136, 62), 14, [
        const Color(0xffd4dca4),
        const Color(0xffa2ad70),
      ]);
    } else {
      box(c, const Rect.fromLTWH(62, 45, 176, 315), 18, [
        const Color(0xffffed64),
        const Color(0xffffd600),
        const Color(0xffbfa300),
      ]);
      screw(c, 150, 76);
      screw(c, 150, 331);
      box(c, const Rect.fromLTWH(83, 151, 134, 147), 13, [
        const Color(0xff2c3141),
        const Color(0xff8694aa),
      ]);
      box(c, const Rect.fromLTWH(88, 157, 124, 133), 9, [
        const Color(0xff4d88ed),
        const Color(0xff1044ad),
      ]);
      label(c, '♿', const Offset(150, 222), 88, Colors.white);
    }
    c.restore();
  }

  @override
  bool shouldRepaint(covariant BellModel oldDelegate) =>
      oldDelegate.state != state || oldDelegate.kind != kind;
}
