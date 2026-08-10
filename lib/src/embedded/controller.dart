import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_mapbox_navigation/src/models/models.dart';

/// Controller for a single MapBox Navigation instance
/// running on the host platform.
class MapBoxNavigationViewController {
  /// Constructor
  MapBoxNavigationViewController(
    int id,
    ValueSetter<RouteEvent>? eventNotifier,
  ) {
    _methodChannel = MethodChannel('flutter_mapbox_navigation/$id');
    _methodChannel.setMethodCallHandler(_handleMethod);

    _eventChannel = EventChannel('flutter_mapbox_navigation/$id/events');
    _routeEventNotifier = eventNotifier;
  }

  late MethodChannel _methodChannel;
  late EventChannel _eventChannel;

  ValueSetter<RouteEvent>? _routeEventNotifier;

  StreamSubscription<RouteEvent>? _routeEventSubscription;
  bool _isDisposed = false;

  ///Current Device OS Version
  Future<String> get platformVersion => _methodChannel
      .invokeMethod('getPlatformVersion')
      .then((dynamic result) => result as String);

  ///Total distance remaining in meters along route.
  Future<double> get distanceRemaining => _methodChannel
      .invokeMethod<double>('getDistanceRemaining')
      .then((dynamic result) => result as double);

  ///Total seconds remaining on all legs.
  Future<double> get durationRemaining => _methodChannel
      .invokeMethod<double>('getDurationRemaining')
      .then((dynamic result) => result as double);

  ///Build the Route Used for the Navigation
  ///
  /// [wayPoints] must not be null. A collection of [WayPoint](longitude,
  /// latitude and name). Must be at least 2 or at most 25. Cannot use
  /// drivingWithTraffic mode if more than 3-waypoints.
  /// [options] options used to generate the route and used while navigating
  ///
  Future<bool> buildRoute({
    required List<WayPoint> wayPoints,
    MapBoxOptions? options,
  }) async {
    if (_isDisposed) return false;
    assert(wayPoints.length > 1, 'Error: WayPoints must be at least 2');
    if (Platform.isIOS && wayPoints.length > 3 && options?.mode != null) {
      assert(
        options!.mode != MapBoxNavigationMode.drivingWithTraffic,
        '''
          Error: Cannot use drivingWithTraffic Mode 
          when you have more than 3 Stops
        ''',
      );
    }
    final pointList = <Map<String, Object?>>[];

    for (var i = 0; i < wayPoints.length; i++) {
      final wayPoint = wayPoints[i];
      assert(wayPoint.name != null, 'Error: waypoints need name');
      assert(wayPoint.latitude != null, 'Error: waypoints need latitude');
      assert(wayPoint.longitude != null, 'Error: waypoints need longitude');

      final pointMap = <String, dynamic>{
        'Order': i,
        'Name': wayPoint.name,
        'Latitude': wayPoint.latitude,
        'Longitude': wayPoint.longitude,
        'IsSilent': wayPoint.isSilent,
      };
      pointList.add(pointMap);
    }

    var i = 0;
    final wayPointMap = {for (final e in pointList) i++: e};

    var args = <String, dynamic>{};
    if (options != null) args = options.toMap();
    args['wayPoints'] = wayPointMap;

    return _methodChannel
        .invokeMethod('buildRoute', args)
        .then((dynamic result) => (result as bool?) ?? false);
  }

  /// starts listening for events
  Future<void> initialize() async {
    if (_isDisposed) return;
    // `cancelOnError: false` matters: a transient platform error must not silently kill the
    // subscription and leave navigation running with no events reaching Dart.
    _routeEventSubscription ??= _streamRouteEvent?.listen(
      _onProgressData,
      onError: (Object error, StackTrace stackTrace) {
        debugPrint('flutter_mapbox_navigation: route event stream error: $error');
      },
      cancelOnError: false,
    );
  }

  /// Clear the built route and resets the map
  Future<bool?> clearRoute() async {
    if (_isDisposed) return false;
    return _methodChannel.invokeMethod('clearRoute');
  }

  /// Starts Free Drive Mode
  Future<bool?> startFreeDrive({MapBoxOptions? options}) async {
    if (_isDisposed) return false;
    Map<String, dynamic>? args;
    if (options != null) args = options.toMap();
    return _methodChannel.invokeMethod('startFreeDrive', args);
  }

  /// Starts the Navigation
  Future<bool?> startNavigation({MapBoxOptions? options}) async {
    if (_isDisposed) return false;
    Map<String, dynamic>? args;
    if (options != null) args = options.toMap();
    return _methodChannel.invokeMethod('startNavigation', args);
  }

  ///Ends Navigation and Closes the Navigation View
  Future<bool?> finishNavigation() async {
    if (_isDisposed) return false;
    final success = await _methodChannel.invokeMethod('finishNavigation');
    return success as bool?;
  }

  /// Performs a safe, idempotent native shutdown of navigation observers and
  /// trip session
  Future<bool?> shutdownNavigation() async {
    if (_isDisposed) return true;
    _isDisposed = true;
    await _routeEventSubscription?.cancel();
    _routeEventSubscription = null;
    _routeEventNotifier = null;
    try {
      final success = await _methodChannel.invokeMethod('shutdownNavigation');
      return (success as bool?) ?? true;
    } catch (_) {
      return false;
    }
  }

  /// Generic Handler for Messages sent from the Platform
  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case 'sendFromNative':
        final text = call.arguments as String?;
        return Future.value('Text from native: $text');
    }
  }

  /// Call this to cancel the subscription to route events
  /// Add here future disposing methods
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _routeEventSubscription?.cancel();
    _routeEventSubscription = null;
    _routeEventNotifier = null;
  }

  void _onProgressData(RouteEvent event) {
    if (!_isDisposed && _routeEventNotifier != null) {
      _routeEventNotifier?.call(event);
    }
  }

  Stream<RouteEvent>? get _streamRouteEvent {
    return _eventChannel
        .receiveBroadcastStream()
        .where((dynamic event) => event != null && event is String)
        .map((dynamic event) => _parseRouteEvent(event as String))
        .where((RouteEvent? event) => event != null)
        .cast<RouteEvent>();
  }

  /// Returns `null` for anything the native side sends that we cannot decode.
  ///
  /// A single malformed payload used to throw straight out of the stream as an unhandled
  /// async `FormatException` — and because the native side can emit events far faster than
  /// once a second (off-route fires continuously under mock locations), that turned one bad
  /// event into a flood. Navigation should never fall over because of one unreadable event.
  RouteEvent? _parseRouteEvent(String jsonString) {
    try {
      final decoded = json.decode(jsonString);
      if (decoded is! Map<String, dynamic>) return null;

      final progressEvent = RouteProgressEvent.fromJson(decoded);
      if (progressEvent.isProgressEvent ?? false) {
        return RouteEvent(
          eventType: MapBoxEvent.progress_change,
          data: progressEvent,
        );
      }
      return RouteEvent.fromJson(decoded);
    } catch (e, stackTrace) {
      debugPrint('flutter_mapbox_navigation: dropped unparseable route event: $e');
      debugPrintStack(stackTrace: stackTrace);
      return null;
    }
  }
}
