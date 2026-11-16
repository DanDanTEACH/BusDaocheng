import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

const appName = '公車報站&導乘';
const categories = ['一般數字路線', '小型路線', '捷運接駁路線', '其他'];
const bridge = MethodChannel('com.led.phone/bridge');
int categoryOf(String label) {
  if (label.startsWith('小') || label.startsWith('市民小巴')) return 1;
  if (label.startsWith('紅') || label.startsWith('藍') || label.startsWith('棕')) {
    return 2;
  }
  if (label.contains('幹線') || label.contains('通勤') || label.contains('快線')) {
    return 3;
  }
  return 0;
}

class BusRoute {
  final Map<String, dynamic> json;
  BusRoute(this.json);
  String get id => json['id'];
  String get label => json['label'];
  String get mapId => json['mapId'];
  Map<String, dynamic> direction(int way) =>
      Map<String, dynamic>.from(json['directions']['$way']);
  List<String> stations(int way) =>
      List<String>.from(direction(way)['stations']);
}

final assetCache = <String, Future<Uint8List?>>{};
Future<Uint8List?> asset(String name) => assetCache.putIfAbsent(
  name,
  () => bridge.invokeMethod<Uint8List>('asset', name),
);
Widget assetImage(String path, {BoxFit fit = BoxFit.contain}) =>
    FutureBuilder<Uint8List?>(
      future: asset(path),
      builder: (c, s) => s.data == null
          ? const SizedBox.shrink()
          : Image.memory(s.data!, fit: fit, gaplessPlayback: true),
    );
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const BusApp());
}

class BusApp extends StatefulWidget {
  const BusApp({super.key});
  @override
  State<BusApp> createState() => BusAppState();
}

class BusAppState extends State<BusApp> with WidgetsBindingObserver {
  List<BusRoute> routes = [];
  List<dynamic> catalog = [];
  Map<String, dynamic> recordings = {}, external = {}, prefs = {};
  int tab = 0,
      section = -1,
      category = 0,
      choice = 0,
      way = 0,
      index = 0,
      mapCategory = 0,
      extChoice = 0,
      extWay = 0,
      bellState = 0;
  int dynamicAccent = 0xffd0bcff;
  bool loading = true,
      active = false,
      playing = false,
      speaking = false,
      automatic = false;
  String query = '', status = '', error = '';
  Timer? advance;
  int audioGeneration = 0;
  Uint8List? background;
  BusRoute get route => routes[choice];
  List<String> get stops => route.stations(way);
  int integer(String key, int fallback) =>
      (prefs[key] as num?)?.toInt() ?? fallback;
  bool boolean(String key, bool fallback) => prefs[key] as bool? ?? fallback;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    bridge.setMethodCallHandler((call) async {
      if (call.method == 'hardwareTap' && !speaking) {
        final kind = call.arguments as int;
        setState(() => bellState = kind);
        await bridge.invokeMethod('bellState', kind);
        await playFile('phone/bells/${kind == 1 ? 'normal' : 'canon'}.m4a');
      }
    });
    load();
  }

  Future<void> load() async {
    try {
      final data = await bridge.invokeMapMethod<String, dynamic>('init') ?? {};
      routes = (jsonDecode(data['routes']) as List)
          .map((r) => BusRoute(Map<String, dynamic>.from(r)))
          .toList();
      catalog = jsonDecode(data['catalog']);
      recordings = jsonDecode(data['audio']);
      external = jsonDecode(data['external']);
      prefs = Map<String, dynamic>.from(data['preferences']);
      dynamicAccent = data['accent'];
      choice = math.max(
        0,
        routes.indexWhere((r) => r.id == prefs['lastRoute']),
      );
      way = integer('lastDirection', 0).clamp(0, 1);
      category = categoryOf(route.label);
      extChoice = math.max(
        0,
        routes.indexWhere((r) => r.id == prefs['externalRoute']),
      );
      extWay = integer('externalDirection', 0).clamp(0, 1);
      index = (data['index'] as int? ?? 0).clamp(0, stops.length - 1);
      if (boolean('customBackground', false)) {
        background = await bridge.invokeMethod<Uint8List>('background');
      }
      setState(() => loading = false);
    } catch (e) {
      setState(() => error = '無法讀取資料：$e');
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      stop();
    } else {
      refreshNative();
    }
  }

  Future<void> refreshNative() async {
    if (loading) return;
    final snapshot = await bridge.invokeMapMethod<String, dynamic>('snapshot');
    if (!mounted || snapshot == null) return;
    setState(() {
      dynamicAccent = snapshot['accent'];
      if (active) {
        index = (snapshot['index'] as int? ?? index).clamp(0, stops.length - 1);
      }
    });
  }

  Future<void> save(String key, dynamic value) async {
    setState(() => prefs[key] = value);
    await bridge.invokeMethod('save', {'key': key, 'value': value});
    if (key == 'appTheme' || key == 'appAccent') {
      final color = await bridge.invokeMethod<int>('accent');
      if (color != null && mounted) setState(() => dynamicAccent = color);
    }
  }

  Future<void> publish() async {
    await bridge.invokeMethod('state', {
      'route': route.id,
      'direction': way,
      'index': index,
    });
  }

  Future<void> stop() async {
    advance?.cancel();
    automatic = false;
    audioGeneration++;
    await bridge.invokeMethod('stop');
    if (mounted) setState(() => speaking = false);
  }

  Future<void> playFile(String path) async {
    if (speaking) return;
    final generation = ++audioGeneration;
    setState(() => speaking = true);
    try {
      final ok = await bridge.invokeMethod<bool>('play', path);
      if (ok == false && mounted) setState(() => status = '音檔無法播放，繼續運作');
    } on PlatformException {
      if (mounted) setState(() => status = '音檔無法播放，繼續運作');
    } finally {
      if (mounted && generation == audioGeneration) {
        setState(() => speaking = false);
      }
    }
  }

  Future<void> announce() async {
    final file = recordings[stops[index]] as String?;
    if (file != null && file.isNotEmpty) {
      await playFile('phone/audio/$file');
    } else {
      setState(() => status = '此站尚缺錄音，仍可繼續切站');
    }
    if (!mounted) return;
    if (automatic) {
      if (index >= stops.length - 1) {
        setState(() => automatic = false);
      } else {
        advance?.cancel();
        advance = Timer(
          Duration(seconds: integer('autoDelaySeconds', 1)),
          () => move(1),
        );
      }
    }
  }

  Future<void> move(int delta) async {
    if (speaking) return;
    setState(() {
      index = (index + delta).clamp(0, stops.length - 1);
      bellState = 0;
      status = '';
    });
    await bridge.invokeMethod('bellState', 0);
    await publish();
    if (delta > 0) await announce();
  }

  Future<void> start() async {
    await stop();
    setState(() {
      active = true;
      playing = true;
      index = 0;
    });
    await publish();
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    await bridge.invokeMethod('keepScreen', true);
  }

  Future<void> leavePlayback() async {
    await stop();
    setState(() => playing = false);
    await SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    await bridge.invokeMethod('keepScreen', false);
  }

  Future<void> back() async {
    if (playing) {
      await leavePlayback();
      setState(() => active = false);
    } else if (section >= 0) {
      setState(() => section = -1);
    } else if (tab != 0) {
      setState(() => tab = 0);
    } else {
      SystemNavigator.pop();
    }
  }

  Color get seed => integer('appAccent', 0) == 5
      ? Color(dynamicAccent)
      : [
          const Color(0xff6aadbd),
          const Color(0xffb699e8),
          const Color(0xff79b890),
          const Color(0xffffb46d),
          const Color(0xffe5858c),
        ][integer('appAccent', 0).clamp(0, 4)];
  @override
  Widget build(BuildContext context) {
    final light = integer('appTheme', 0) == 1;
    return MaterialApp(
      title: appName,
      debugShowCheckedModeBanner: false,
      locale: const Locale('zh', 'TW'),
      supportedLocales: const [Locale('zh', 'TW')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: seed,
          brightness: light ? Brightness.light : Brightness.dark,
        ),
        dialogTheme: DialogThemeData(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
            side: BorderSide(color: seed.withValues(alpha: .6)),
          ),
        ),
        scaffoldBackgroundColor: light
            ? const Color(0xfff9f5ff)
            : integer('appTheme', 0) == 2
            ? const Color(0xff101c30)
            : const Color(0xff141218),
      ),
      home: Builder(
        builder: (context) {
          if (error.isNotEmpty) {
            return Scaffold(body: Center(child: Text(error)));
          }
          if (loading) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          return PopScope(
            canPop: false,
            onPopInvokedWithResult: (didPop, result) {
              if (!didPop) back();
            },
            child: Scaffold(
              body: playing
                  ? playback(context)
                  : Stack(
                      children: [
                        if (background != null)
                          Positioned.fill(
                            child: Opacity(
                              opacity: .18,
                              child: Image.memory(
                                background!,
                                fit: BoxFit.cover,
                              ),
                            ),
                          ),
                        SafeArea(
                          child: AnimatedSwitcher(
                            duration: const Duration(milliseconds: 300),
                            child: KeyedSubtree(
                              key: ValueKey('$tab/$section'),
                              child: [
                                selection(context),
                                maps(context),
                                equipment(context),
                                settings(context),
                              ][tab],
                            ),
                          ),
                        ),
                      ],
                    ),
              bottomNavigationBar: playing
                  ? null
                  : NavigationBar(
                      selectedIndex: tab,
                      onDestinationSelected: (v) {
                        setState(() {
                          tab = v;
                          section = -1;
                        });
                      },
                      destinations: const [
                        NavigationDestination(
                          icon: Icon(Icons.play_circle_outline),
                          label: '播放',
                        ),
                        NavigationDestination(
                          icon: Icon(Icons.map_outlined),
                          label: '路線圖',
                        ),
                        NavigationDestination(
                          icon: Icon(Icons.tune),
                          label: '設備',
                        ),
                        NavigationDestination(
                          icon: Icon(Icons.settings_outlined),
                          label: '設定',
                        ),
                      ],
                    ),
            ),
          );
        },
      ),
    );
  }

  Widget header(
    BuildContext c,
    String title,
    String description, {
    Widget? trailing,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                appName,
                style: TextStyle(
                  fontSize: 13,
                  color: Theme.of(c).colorScheme.primary,
                ),
              ),
              const SizedBox(height: 6),
              Text(title, style: Theme.of(c).textTheme.headlineLarge),
              const SizedBox(height: 4),
              Text(description, style: Theme.of(c).textTheme.bodyMedium),
            ],
          ),
        ),
        ?trailing,
      ],
    ),
  );
  Widget dropdown<T>(
    String label,
    T value,
    List<DropdownMenuItem<T>> items,
    ValueChanged<T?> changed,
  ) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: DropdownButtonFormField<T>(
      key: ValueKey("$label/$value"),
      initialValue: value,
      isExpanded: true,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      items: items,
      onChanged: changed,
    ),
  );
  Widget selection(BuildContext c) {
    final choices = routes
        .asMap()
        .entries
        .where((r) => categoryOf(r.value.label) == category)
        .toList();
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        header(
          c,
          '播放',
          '選擇路線與方向',
          trailing: SizedBox(
            width: 108,
            height: 66,
            child: assetImage('phone/maps/${route.mapId}_1.png'),
          ),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                dropdown<int>(
                  '分類',
                  category,
                  List.generate(
                    4,
                    (i) =>
                        DropdownMenuItem(value: i, child: Text(categories[i])),
                  ),
                  (v) {
                    if (v == null) return;
                    setState(() {
                      category = v;
                      choice = routes.indexWhere(
                        (r) => categoryOf(r.label) == v,
                      );
                      way = 0;
                    });
                  },
                ),
                dropdown<int>(
                  '路線',
                  choice,
                  choices
                      .map(
                        (e) => DropdownMenuItem(
                          value: e.key,
                          child: Text(e.value.label),
                        ),
                      )
                      .toList(),
                  (v) {
                    if (v != null) {
                      setState(() {
                        choice = v;
                        way = 0;
                      });
                    }
                  },
                ),
                TextButton.icon(
                  onPressed: () => searchRoute(c),
                  icon: const Icon(Icons.search),
                  label: const Text('搜尋路線'),
                ),
                dropdown<int>(
                  '方向',
                  way,
                  List.generate(
                    2,
                    (i) => DropdownMenuItem(
                      value: i,
                      child: Text(
                        '${i == 0 ? '去程' : '返程'} · 往 ${route.direction(i)['to']}',
                      ),
                    ),
                  ),
                  (v) {
                    if (v != null) setState(() => way = v);
                  },
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: stops.isEmpty ? null : start,
          child: const Text('確認'),
        ),
      ],
    );
  }

  Future<void> searchRoute(BuildContext c) async {
    String q = '';
    await showDialog<void>(
      context: c,
      builder: (d) => StatefulBuilder(
        builder: (c, update) => AlertDialog(
          title: const Text('搜尋路線'),
          content: SizedBox(
            width: 440,
            height: 340,
            child: Column(
              children: [
                TextField(
                  autofocus: true,
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: '輸入路線名稱',
                  ),
                  onChanged: (s) => update(() => q = s),
                ),
                Expanded(
                  child: ListView(
                    children: routes
                        .asMap()
                        .entries
                        .where(
                          (e) => e.value.label.toLowerCase().contains(
                            q.toLowerCase(),
                          ),
                        )
                        .map(
                          (e) => ListTile(
                            title: Text(e.value.label),
                            onTap: () {
                              setState(() {
                                choice = e.key;
                                category = categoryOf(route.label);
                                way = 0;
                              });
                              Navigator.pop(d);
                            },
                          ),
                        )
                        .toList(),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(d),
              child: const Text('取消'),
            ),
          ],
        ),
      ),
    );
  }

  int catalogCategory(Map item) {
    final match = routes.where((r) => r.mapId == item['id']);
    return categoryOf(match.isNotEmpty ? match.first.label : item['label']);
  }

  Widget maps(BuildContext c) {
    final items = catalog
        .where(
          (e) =>
              catalogCategory(e) == mapCategory &&
              '${e['label']} ${e['id']}'.toLowerCase().contains(
                query.toLowerCase(),
              ),
        )
        .toList();
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          header(c, '路線圖', '分類搜尋與查看路線圖'),
          Row(
            children: [
              Expanded(
                child: dropdown<int>(
                  '類別',
                  mapCategory,
                  List.generate(
                    4,
                    (i) =>
                        DropdownMenuItem(value: i, child: Text(categories[i])),
                  ),
                  (v) {
                    if (v != null) setState(() => mapCategory = v);
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: TextField(
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: '搜尋路線或檔案編號',
                    border: OutlineInputBorder(),
                  ),
                  onChanged: (s) => setState(() => query = s),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.separated(
              itemCount: items.length,
              separatorBuilder: (_, i) => const Divider(height: 1),
              itemBuilder: (c, i) => ListTile(
                title: Text(items[i]['label']),
                subtitle: Text(items[i]['id']),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => viewMap(c, 'phone/catalog/${items[i]['file']}'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> viewMap(BuildContext c, String path) async {
    await showDialog<void>(
      context: c,
      useSafeArea: false,
      builder: (d) => Dialog.fullscreen(
        backgroundColor: Colors.black,
        child: Stack(
          children: [
            Positioned.fill(
              child: InteractiveViewer(
                minScale: .5,
                maxScale: 8,
                child: Center(child: assetImage(path)),
              ),
            ),
            Positioned(
              top: 16,
              right: 16,
              child: SafeArea(
                child: IconButton.filledTonal(
                  onPressed: () => Navigator.pop(d),
                  icon: const Icon(Icons.close),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Map get externalInfo => external[routes[extChoice].id]?['$extWay'] ?? {};
  Widget equipment(BuildContext c) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      header(c, '設備', '下車鈴與對外廣播'),
      Card(
        child: ListTile(
          title: const Text('下車鈴'),
          subtitle: const Text('一般下車鈴與卡農下車鈴'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => bells(c),
        ),
      ),
      const SizedBox(height: 16),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('對外廣播', style: Theme.of(c).textTheme.titleLarge),
              dropdown<int>(
                '廣播路線',
                extChoice,
                List.generate(
                  routes.length,
                  (i) =>
                      DropdownMenuItem(value: i, child: Text(routes[i].label)),
                ),
                (v) {
                  if (v != null) {
                    setState(() => extChoice = v);
                    save('externalRoute', routes[v].id);
                  }
                },
              ),
              dropdown<int>(
                '廣播方向',
                extWay,
                List.generate(
                  2,
                  (i) => DropdownMenuItem(
                    value: i,
                    child: Text(
                      '${i == 0 ? '去程' : '返程'} · 往 ${routes[extChoice].direction(i)['to']}',
                    ),
                  ),
                ),
                (v) {
                  if (v != null) {
                    setState(() => extWay = v);
                    save('externalDirection', v);
                  }
                },
              ),
              FilledButton(
                onPressed: speaking || externalInfo['available'] != true
                    ? null
                    : () => playFile('phone/external/${externalInfo['file']}'),
                child: const Text('對外廣播'),
              ),
            ],
          ),
        ),
      ),
    ],
  );
  Future<void> bells(BuildContext c) async {
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    if (!c.mounted) return;
    await Navigator.of(c).push(
      MaterialPageRoute<void>(
        builder: (page) => StatefulBuilder(
          builder: (c, update) => Scaffold(
            appBar: AppBar(
              title: const Text('下車鈴'),
              actions: [
                TextButton(
                  onPressed: () {
                    bellState = 0;
                    bridge.invokeMethod('bellState', 0);
                    update(() {});
                  },
                  child: const Text('重設'),
                ),
              ],
            ),
            body: LayoutBuilder(
              builder: (c, size) {
                final children = List.generate(
                  3,
                  (kind) => Expanded(
                    child: Column(
                      children: [
                        Expanded(
                          child: GestureDetector(
                            onTap: kind == 0 || speaking
                                ? null
                                : () async {
                                    if (speaking) return;
                                    bellState = kind;
                                    await bridge.invokeMethod(
                                      'bellState',
                                      kind,
                                    );
                                    update(() {});
                                    await playFile(
                                      'phone/bells/${kind == 1 ? 'normal' : 'canon'}.m4a',
                                    );
                                    if (c.mounted) update(() {});
                                  },
                            child: Semantics(
                              label: ['下車鈴指示器', '按下一般下車鈴', '按下卡農下車鈴'][kind],
                              button: kind != 0,
                              child: AndroidView(
                                viewType: 'bus/hardware',
                                creationParams: {'kind': kind},
                                creationParamsCodec:
                                    const StandardMessageCodec(),
                              ),
                            ),
                          ),
                        ),
                        Text(['下車鈴指示器', '一般下車鈴', '卡農下車鈴'][kind]),
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
                );
                return Row(children: children);
              },
            ),
          ),
        ),
      ),
    );
    await SystemChrome.setPreferredOrientations(DeviceOrientation.values);
  }

  Future<void> numberDialog(BuildContext c, {bool begin = false}) async {
    final input = TextEditingController(
      text: '${integer('autoDelaySeconds', 1)}',
    );
    String? problem;
    await showDialog<void>(
      context: c,
      builder: (d) => StatefulBuilder(
        builder: (c, update) => AlertDialog(
          title: Text(begin ? '自動播報設定' : '自動播放間隔'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('每站音檔播放完畢後，等待幾秒再切換下一站？'),
              TextField(
                controller: input,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  suffixText: '秒',
                  errorText: problem,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(d),
              child: const Text('取消'),
            ),
            TextButton(
              onPressed: () async {
                final n = int.tryParse(input.text);
                if (n == null || n < 0 || n > 3600) {
                  update(() => problem = '請輸入 0～3600 的整數');
                  return;
                }
                await save('autoDelaySeconds', n);
                if (d.mounted) Navigator.pop(d);
                if (begin) {
                  setState(() => automatic = true);
                  announce();
                }
              },
              child: const Text('確認'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> pick(
    BuildContext c,
    String title,
    String key,
    List<String> options, {
    Set<int> disabled = const {},
  }) async {
    await showDialog<void>(
      context: c,
      builder: (d) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 400,
          child: RadioGroup<int>(
            groupValue: integer(key, 0),
            onChanged: (v) {
              if (v != null && !disabled.contains(v)) {
                save(key, v);
                Navigator.pop(d);
              }
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: List.generate(
                options.length,
                (i) => RadioListTile<int>(
                  title: Text(options[i]),
                  value: i,
                  enabled: !disabled.contains(i),
                ),
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(d),
            child: const Text('取消'),
          ),
        ],
      ),
    );
  }

  Widget settings(BuildContext c) {
    final titles = ['播放顯示', '音檔與自動播放', '應用程式外觀'];
    final hints = ['時鐘、秒數與播放畫面主題', '站名音檔與自動播放間隔', '主題、強調色與背景照片'];
    final items = <Widget>[];
    Widget setting(String name, String summary, VoidCallback action) =>
        ListTile(title: Text(name), subtitle: Text(summary), onTap: action);
    if (section < 0) {
      for (var i = 0; i < 3; i++) {
        final value = i;
        items.add(
          setting(titles[i], hints[i], () => setState(() => section = value)),
        );
      }
    }
    if (section == 0) {
      items.addAll([
        SwitchListTile(
          title: const Text('播放時顯示時鐘'),
          value: boolean('showClock', true),
          onChanged: (v) => save('showClock', v),
        ),
        SwitchListTile(
          title: const Text('時鐘顯示秒數'),
          value: boolean('showSeconds', true),
          onChanged: boolean('showClock', true)
              ? (v) => save('showSeconds', v)
              : null,
        ),
        setting(
          '播放畫面主題',
          ['原版黑底', '深藍', '明亮'][integer('playbackTheme', 0)],
          () => pick(c, '播放畫面主題', 'playbackTheme', ['原版黑底', '深藍', '明亮']),
        ),
      ]);
    }
    if (section == 1) {
      items.addAll([
        setting(
          '站名音檔廠牌',
          '立皓',
          () => pick(
            c,
            '站名音檔廠牌',
            'audioBrand',
            ['立皓', '寶錄（尚未啟用）'],
            disabled: {1},
          ),
        ),
        setting(
          '自動播放間隔',
          '${integer('autoDelaySeconds', 1)} 秒',
          () => numberDialog(c),
        ),
      ]);
    }
    if (section == 2) {
      items.addAll([
        setting(
          '應用程式主題',
          ['經典深色', '明亮', '深藍'][integer('appTheme', 0)],
          () => pick(c, '應用程式主題', 'appTheme', ['經典深色', '明亮', '深藍']),
        ),
        setting(
          '強調色',
          ['天空藍', '紫色', '綠色', '橙色', '紅色', '跟隨系統'][integer(
            'appAccent',
            0,
          ).clamp(0, 5)],
          () => pick(c, '強調色', 'appAccent', [
            '天空藍',
            '紫色',
            '綠色',
            '橙色',
            '紅色',
            '跟隨系統',
          ]),
        ),
        setting('跟隨系統', 'Android 12 以上使用系統動態強調色', () => save('appAccent', 5)),
        setting('背景照片', background == null ? '尚未設定' : '已使用自訂照片', () async {
          final bytes = await bridge.invokeMethod<Uint8List>('pickBackground');
          if (bytes != null) {
            setState(() => background = bytes);
            save('customBackground', true);
          }
        }),
        setting('清除背景照片', '', () {
          setState(() => background = null);
          save('customBackground', false);
        }),
      ]);
    }
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        header(
          c,
          section < 0 ? '設定' : titles[section],
          section < 0 ? '調整播放與應用程式偏好' : hints[section],
          trailing: section < 0
              ? null
              : IconButton(
                  onPressed: () => setState(() => section = -1),
                  icon: const Icon(Icons.arrow_back),
                ),
        ),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              for (int i = 0; i < items.length; i++) ...[
                items[i],
                if (i < items.length - 1) const Divider(height: 1),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget playback(BuildContext c) => LayoutBuilder(
    builder: (c, size) {
      final theme = integer('playbackTheme', 0);
      return ColoredBox(
        color: [
          Colors.black,
          const Color(0xff092038),
          const Color(0xfff5f5ed),
        ][theme],
        child: Column(
          children: [
            Expanded(
              child: Stack(
                children: [
                  Positioned.fill(
                    child: StationDisplay(
                      stops: stops,
                      index: index,
                      route: route,
                      way: way,
                      theme: theme,
                      showClock: boolean('showClock', true),
                      seconds: boolean('showSeconds', true),
                    ),
                  ),
                  Positioned(
                    bottom: 0,
                    right: 8,
                    child: SizedBox(
                      width: math.min(370, size.maxWidth - 16),
                      height: 42,
                      child: Row(
                        children: [
                          for (final entry in <(String, VoidCallback?)>[
                            (
                              '上一站',
                              !speaking && !automatic && index > 0
                                  ? () => move(-1)
                                  : null,
                            ),
                            (
                              '下一站',
                              !speaking &&
                                      !automatic &&
                                      index < stops.length - 1
                                  ? () => move(1)
                                  : null,
                            ),
                            (
                              automatic ? '停止' : '自動播報',
                              automatic
                                  ? () => stop()
                                  : speaking
                                  ? null
                                  : () => numberDialog(c, begin: true),
                            ),
                            (
                              '選擇路線',
                              () async {
                                await leavePlayback();
                                setState(() => active = false);
                              },
                            ),
                          ])
                            Expanded(
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 3,
                                ),
                                child: FilledButton.tonal(
                                  style: FilledButton.styleFrom(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 4,
                                    ),
                                  ),
                                  onPressed: entry.$2,
                                  child: FittedBox(
                                    child: Text(entry.$1, maxLines: 1),
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 64),
          ],
        ),
      );
    },
  );
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    advance?.cancel();
    super.dispose();
  }
}

class StationDisplay extends StatefulWidget {
  final List<String> stops;
  final int index, way, theme;
  final BusRoute route;
  final bool showClock, seconds;
  const StationDisplay({
    super.key,
    required this.stops,
    required this.index,
    required this.route,
    required this.way,
    required this.theme,
    required this.showClock,
    required this.seconds,
  });
  @override
  State<StationDisplay> createState() => StationDisplayState();
}

class StationDisplayState extends State<StationDisplay>
    with SingleTickerProviderStateMixin {
  late final AnimationController ticker = AnimationController(
    vsync: this,
    duration: const Duration(days: 1),
  )..repeat();
  double start = 0, target = 0;
  int changed = 0;
  final watch = Stopwatch()..start();
  @override
  void initState() {
    super.initState();
    target = math
        .max(0, math.min(widget.index - 2, widget.stops.length - 6))
        .toDouble();
    start = target;
  }

  double get column {
    final t = ((watch.elapsedMilliseconds - changed) / 320).clamp(0.0, 1.0);
    return start + (target - start) * Curves.easeInOut.transform(t);
  }

  @override
  void didUpdateWidget(StationDisplay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.index != widget.index) {
      start = column;
      target = math
          .max(0, math.min(widget.index - 2, widget.stops.length - 6))
          .toDouble();
      changed = watch.elapsedMilliseconds;
    }
  }

  @override
  Widget build(BuildContext c) => AnimatedBuilder(
    animation: ticker,
    builder: (c, child) => LayoutBuilder(
      builder: (c, size) {
        final scale = math.min(
          size.maxWidth / 1920,
          math.max(1, size.maxHeight - 42) / 560,
        );
        final w = 1920 * scale, h = 560 * scale;
        String map = widget.route.mapId;
        if (widget.way == 1 && map.endsWith('0A')) {
          map = '${map.substring(0, map.length - 1)}B';
        }
        return Center(
          child: SizedBox(
            width: w,
            height: h,
            child: Stack(
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: StopsPainter(
                      widget,
                      column,
                      watch.elapsedMilliseconds,
                    ),
                  ),
                ),
                Positioned(
                  left: 0,
                  top: 60 * scale,
                  width: 700 * scale,
                  height: 190 * scale,
                  child: FutureBuilder<Uint8List?>(
                    future: asset('phone/maps/${map}_1.png'),
                    builder: (c, s) => s.data == null
                        ? assetImage('phone/maps/${widget.route.mapId}_1.png')
                        : Image.memory(s.data!, fit: BoxFit.fill),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    ),
  );
  @override
  void dispose() {
    ticker.dispose();
    super.dispose();
  }
}

class StopsPainter extends CustomPainter {
  final StationDisplay data;
  final double column;
  final int millis;
  StopsPainter(this.data, this.column, this.millis);
  void text(
    Canvas c,
    String s,
    double x,
    double y,
    double font,
    Color color, {
    double? maxWidth,
  }) {
    var p = TextPainter(
      text: TextSpan(
        text: s,
        style: TextStyle(
          fontSize: font,
          color: color,
          fontWeight: FontWeight.bold,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    if (maxWidth != null && p.width > maxWidth) {
      p = TextPainter(
        text: TextSpan(
          text: s,
          style: TextStyle(
            fontSize: font * maxWidth / p.width,
            color: color,
            fontWeight: FontWeight.bold,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
    }
    p.paint(c, Offset(x, y - p.height));
  }

  @override
  void paint(Canvas c, Size size) {
    c.save();
    c.scale(size.width / 1920, size.height / 560);
    final light = data.theme == 2;
    final white = light ? Colors.black : Colors.white;
    final paint = Paint()..color = const Color(0xff6aadbd);
    text(c, '終點站：', 10, 330, 60, white);
    text(
      c,
      data.route.direction(data.way)['to'],
      310,
      330,
      60,
      white,
      maxWidth: 380,
    );
    final now = DateTime.now();
    String two(int n) => n.toString().padLeft(2, '0');
    text(
      c,
      '${now.year}年${two(now.month)}月${two(now.day)}日',
      10,
      505,
      60,
      const Color(0xff39a68d),
    );
    if (data.showClock) {
      text(
        c,
        '${two(now.hour)}:${two(now.minute)}${data.seconds ? ':${two(now.second)}' : ''}',
        10,
        555,
        48,
        const Color(0xff39a68d),
      );
    }
    c.save();
    c.clipRect(const Rect.fromLTRB(716, 0, 1860, 560));
    c.drawRect(const Rect.fromLTWH(716, 27, 1144, 5), paint);
    const slot = 1144 / 6;
    for (
      int at = math.max(0, column.floor());
      at < math.min(data.stops.length, (column + 6).ceil());
      at++
    ) {
      final x = 716 + slot * (at - column + .5),
          color = at == data.index ? const Color(0xffffb34f) : white;
      paint
        ..color = Colors.black
        ..style = PaintingStyle.fill;
      c.drawCircle(Offset(x, 29), 12.5, paint);
      paint
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2;
      c.drawCircle(Offset(x, 29), 12.5, paint);
      paint.style = PaintingStyle.fill;
      final chars = data.stops[at]
          .replaceAll('(', '︵')
          .replaceAll(')', '︶')
          .replaceAll('（', '︵')
          .replaceAll('）', '︶')
          .replaceAll('-', '｜')
          .runes
          .map((r) => String.fromCharCode(r))
          .toList();
      const font = 42.0;
      final height = chars.length * font,
          cycle = height + font,
          offset = height > 504 ? (millis * .06) % cycle : 0.0;
      c.save();
      c.clipRect(Rect.fromLTRB(x - slot / 2, 46, x + slot / 2, 550));
      for (int j = 0; j < chars.length; j++) {
        text(c, chars[j], x - font / 2, 82 + j * font - offset, font, color);
        if (height > 504) {
          text(
            c,
            chars[j],
            x - font / 2,
            82 + j * font - offset + cycle,
            font,
            color,
          );
        }
      }
      c.restore();
    }
    c.restore();
    c.restore();
  }

  @override
  bool shouldRepaint(covariant StopsPainter old) => true;
}
