import Flutter
import UIKit
import MapboxMaps
import MapboxDirections
import MapboxCoreNavigation
import MapboxNavigation

public class NavigationFactory : NSObject, FlutterStreamHandler
{
    var _navigationViewController: NavigationViewController? = nil
    var _eventSink: FlutterEventSink? = nil
    
    let ALLOW_ROUTE_SELECTION = false
    let IsMultipleUniqueRoutes = false
    var isEmbeddedNavigation = false
    
    var _distanceRemaining: Double?
    var _durationRemaining: Double?
    var _navigationMode: String?
    var _routes: [Route]?
    var _wayPointOrder = [Int:Waypoint]()
    var _wayPoints = [Waypoint]()
    var _lastKnownLocation: CLLocation?
    
    var _options: NavigationRouteOptions?
    var _simulateRoute = false
    var _allowsUTurnAtWayPoints: Bool?
    var _isOptimized = false
    var _language = "en"
    var _voiceUnits = "imperial"
    var _mapStyleUrlDay: String?
    var _mapStyleUrlNight: String?
    var _isDarkTheme = false
    var _arrivalRadius: Double?
    var _zoom: Double = 13.0
    var _tilt: Double = 0.0
    var _bearing: Double = 0.0
    var _animateBuildRoute = true
    var _longPressDestinationEnabled = true
    var _alternatives = true
    var _shouldReRoute = true
    var _showReportFeedbackButton = true
    var _showEndOfRouteFeedback = true
    var _enableOnMapTapCallback = false
    var navigationDirections: Directions?
    
    func getTopViewController() -> UIViewController? {
        var topVC: UIViewController? = nil
        if #available(iOS 13.0, *) {
            topVC = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first(where: { $0.isKeyWindow })?.rootViewController
        }
        if topVC == nil {
            topVC = UIApplication.shared.delegate?.window??.rootViewController
        }
        while let presented = topVC?.presentedViewController {
            topVC = presented
        }
        return topVC
    }
    
    func addWayPoints(arguments: NSDictionary?, result: @escaping FlutterResult)
    {

        guard var locations = getLocationsFromFlutterArgument(arguments: arguments) else { return }

        var nextIndex = 1
        for loc in locations
        {
            let wayPoint = Waypoint(coordinate: CLLocationCoordinate2D(latitude: loc.latitude!, longitude: loc.longitude!), name: loc.name)
            wayPoint.separatesLegs = !loc.isSilent
            if (_wayPoints.count >= nextIndex) {
                _wayPoints.insert(wayPoint, at: nextIndex)
            }
            else {
                _wayPoints.append(wayPoint)
            }
            nextIndex += 1
        }
        
        startNavigationWithWayPoints(wayPoints: _wayPoints, flutterResult: result, isUpdatingWaypoints: true)
    }
    
    func startFreeDrive(arguments: NSDictionary?, result: @escaping FlutterResult)
    {
        let freeDriveViewController = FreeDriveViewController()
        if let topVC = getTopViewController() {
            topVC.present(freeDriveViewController, animated: true, completion: nil)
        }
    }
    
    func startNavigation(arguments: NSDictionary?, result: @escaping FlutterResult)
    {
        _wayPoints.removeAll()
        _wayPointOrder.removeAll()
        
        guard var locations = getLocationsFromFlutterArgument(arguments: arguments) else { return }
        
        for loc in locations
        {
            let location = Waypoint(coordinate: CLLocationCoordinate2D(latitude: loc.latitude!, longitude: loc.longitude!), name: loc.name)
            
            location.separatesLegs = !loc.isSilent
            
            _wayPoints.append(location)
            _wayPointOrder[loc.order!] = location
        }
        
        parseFlutterArguments(arguments: arguments)
        
        _options?.includesAlternativeRoutes = _alternatives
        
        if(_wayPoints.count > 3 && arguments?["mode"] == nil)
        {
            _navigationMode = "driving"
        }
        
        if(_wayPoints.count > 0)
        {
            if(IsMultipleUniqueRoutes)
            {
                startNavigationWithWayPoints(wayPoints: [_wayPoints.remove(at: 0), _wayPoints.remove(at: 0)], flutterResult: result, isUpdatingWaypoints: false)
            }
            else
            {
                startNavigationWithWayPoints(wayPoints: _wayPoints, flutterResult: result, isUpdatingWaypoints: false)
            }
            
        }
    }
    
    
    func startNavigationWithWayPoints(wayPoints: [Waypoint], flutterResult: @escaping FlutterResult, isUpdatingWaypoints: Bool)
    {
        let simulationMode: SimulationMode = _simulateRoute ? .always : .never
        setNavigationOptions(wayPoints: wayPoints)
        
        Directions.shared.calculate(_options!) { [weak self](session, result) in
            guard let strongSelf = self else { return }
            switch result {
            case .failure(let error):
                strongSelf.sendEvent(eventType: MapBoxEventType.route_build_failed)
                flutterResult("An error occured while calculating the route \(error.localizedDescription)")
            case .success(let response):
                guard let routes = response.routes else { return }
                //TODO: if more than one route found, give user option to select one: DOES NOT WORK
                if(routes.count > 1 && strongSelf.ALLOW_ROUTE_SELECTION)
                {
                    //show map to select a specific route
                    strongSelf._routes = routes
                    let routeOptionsView = RouteOptionsViewController(routes: routes, options: strongSelf._options!)
                    
                    if let topVC = strongSelf.getTopViewController() {
                        topVC.present(routeOptionsView, animated: true, completion: nil)
                    }
                }
                else
                {
                    let navLocationManager = strongSelf._simulateRoute ? SimulatedLocationManager(route: response.routes!.first!) : NavigationLocationManager()
                    if let navLocManager = navLocationManager as? NavigationLocationManager {
                        navLocManager.distanceFilter = 1.5
                        navLocManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
                        navLocManager.activityType = .automotiveNavigation
                        navLocManager.headingFilter = 3.0
                        navLocManager.pausesLocationUpdatesAutomatically = false
                    }
                    let navigationService = MapboxNavigationService(
                        routeResponse: response,
                        routeIndex: 0,
                        routeOptions: strongSelf._options!,
                        locationSource: navLocationManager,
                        simulating: simulationMode
                    )
                    var dayStyle = CustomDayStyle()
                    if(strongSelf._mapStyleUrlDay != nil){
                        dayStyle = CustomDayStyle(url: strongSelf._mapStyleUrlDay)
                    }
                    let nightStyle = CustomNightStyle()
                    if(strongSelf._mapStyleUrlNight != nil){
                        nightStyle.mapStyleURL = URL(string: strongSelf._mapStyleUrlNight!)!
                    }
                    let isDark = strongSelf._isDarkTheme || (strongSelf._mapStyleUrlDay?.lowercased().contains("dark") == true)
                    let styles: [MapboxNavigation.Style] = isDark ? [nightStyle, dayStyle] : [dayStyle, nightStyle]
                    
                    // Fix 3: Apply UIAppearance proxies before NavigationViewController is initialized
                    styles.first?.apply()
                    
                    let navigationOptions = NavigationOptions(styles: styles, navigationService: navigationService)
                    if (isUpdatingWaypoints) {
                        strongSelf._navigationViewController?.navigationService.router.updateRoute(with: IndexedRouteResponse(routeResponse: response, routeIndex: 0), routeOptions: strongSelf._options) { success in
                            if (success) {
                                flutterResult("true")
                            } else {
                                flutterResult("failed to add stop")
                            }
                        }
                    }
                    else {
                        strongSelf.startNavigation(routeResponse: response, options: strongSelf._options!, navOptions: navigationOptions)
                    }
                }
            }
        }
        
    }
    
    func startNavigation(routeResponse: RouteResponse, options: NavigationRouteOptions, navOptions: NavigationOptions)
    {
        isEmbeddedNavigation = false
        if(self._navigationViewController == nil)
        {
            self._navigationViewController = NavigationViewController(for: routeResponse, routeIndex: 0, routeOptions: options, navigationOptions: navOptions)
            self._navigationViewController!.modalPresentationStyle = .fullScreen
            self._navigationViewController!.delegate = self
            self._navigationViewController!.routeLineTracksTraversal = true
            self._navigationViewController!.navigationMapView!.localizeLabels()
            self._navigationViewController!.showsReportFeedback = _showReportFeedbackButton
            self._navigationViewController!.showsEndOfRouteFeedback = _showEndOfRouteFeedback
            
            // Fix 1: Override route line color to Epic green
            if let navMapView = self._navigationViewController!.navigationMapView {
                navMapView.trafficUnknownColor = UIColor(red: 97.0/255.0, green: 203.0/255.0, blue: 8.0/255.0, alpha: 1.0)
                navMapView.trafficLowColor    = UIColor(red: 97.0/255.0, green: 203.0/255.0, blue: 8.0/255.0, alpha: 1.0)
                navMapView.trafficModerateColor = UIColor(red: 97.0/255.0, green: 203.0/255.0, blue: 8.0/255.0, alpha: 1.0)
                navMapView.trafficHeavyColor  = UIColor(red: 97.0/255.0, green: 203.0/255.0, blue: 8.0/255.0, alpha: 1.0)
                navMapView.trafficSevereColor = UIColor(red: 97.0/255.0, green: 203.0/255.0, blue: 8.0/255.0, alpha: 1.0)
                navMapView.routeCasingColor   = UIColor(red: 11.0/255.0, green: 39.0/255.0, blue: 0.0/255.0, alpha: 1.0)
            }
        }
        if let topVC = getTopViewController() {
            topVC.present(self._navigationViewController!, animated: true) { [weak self] in
                guard let self = self, let navVC = self._navigationViewController else { return }
                let isDarkMode = self._isDarkTheme || (self._mapStyleUrlDay?.lowercased().contains("dark") == true)
                self.applyEpicThemeToNavVC(navVC, isDark: isDarkMode)
            }
        }
    }
    
    // ─── Epic Theme Direct Application ───────────────────────────────────────
    /// Direct view hierarchy traversal for Epic theme enforcement
    func applyEpicThemeToNavVC(_ navVC: NavigationViewController, isDark: Bool) {
        let darkestGreen = UIColor(red: 11.0/255, green: 39.0/255, blue: 0.0/255, alpha: 1)   // #0B2700
        let darkSurface  = UIColor(red: 24.0/255, green: 56.0/255, blue: 20.0/255, alpha: 1)  // #183814
        let lightSurface = UIColor(red: 255.0/255, green: 255.0/255, blue: 255.0/255, alpha: 1)
        let maneuverBg   = UIColor(red: 249.0/255, green: 250.0/255, blue: 249.0/255, alpha: 1)
        let brandGreen   = UIColor(red: 97.0/255, green: 203.0/255, blue: 8.0/255, alpha: 1)  // #61CB08
        let textWhite    = UIColor.white
        let textDark     = UIColor(red: 15.0/255, green: 18.0/255, blue: 16.0/255, alpha: 1)
        
        let bannerBg  = isDark ? darkestGreen : maneuverBg
        let bottomBg  = isDark ? darkSurface  : lightSurface
        let textColor = isDark ? textWhite    : textDark
        
        navVC.showsReportFeedback = _showReportFeedbackButton
        
        applyToAllSubviews(of: navVC.view) { view in
            let typeName = String(describing: type(of: view))
            
            // 1. Hide report feedback button (chat icon) if disabled
            if !_showReportFeedbackButton {
                if typeName.contains("Report") || typeName.contains("Feedback") {
                    view.isHidden = true
                    view.alpha = 0
                    return
                }
            }

            // 2. Top banners, Sub maneuvers, Next step banners, Information stack
            if typeName.contains("InstructionsBanner") || typeName.contains("TopBanner") ||
               typeName.contains("StepInstructions") || typeName.contains("InformationStack") ||
               typeName.contains("ManeuverContainer") || typeName.contains("NextBanner") ||
               typeName.contains("NextStep") || typeName.contains("StatusView") ||
               typeName.contains("LanesView") || typeName.contains("LaneView") {
                view.backgroundColor = bannerBg
            }
            
            // 3. Steps Table View (Expanded turn-by-turn list) & Cells
            if typeName.contains("Steps") || typeName.contains("StepTable") || view is UITableView {
                view.backgroundColor = bannerBg
                if let tableView = view as? UITableView {
                    tableView.backgroundColor = bannerBg
                    tableView.separatorColor = isDark ? darkSurface : UIColor.lightGray
                }
            }
            if view is UITableViewCell || typeName.contains("StepCell") || typeName.contains("StepTableViewCell") {
                view.backgroundColor = bannerBg
                if let cell = view as? UITableViewCell {
                    cell.backgroundColor = bannerBg
                    cell.contentView.backgroundColor = bannerBg
                    cell.textLabel?.textColor = textColor
                    cell.detailTextLabel?.textColor = textColor
                }
            }

            // 4. Bottom banners & footer bars (including Close button bar)
            if typeName.contains("BottomBanner") || typeName.contains("BottomPadding") ||
               typeName.contains("ArrivalView") || typeName.contains("Footer") {
                view.backgroundColor = bottomBg
            }

            // 5. Resume Button (recenter button when map panned)
            if typeName.contains("ResumeButton") {
                view.backgroundColor = isDark ? darkSurface : lightSurface
                if let button = view as? UIButton {
                    button.tintColor = isDark ? brandGreen : darkestGreen
                    button.setTitleColor(isDark ? brandGreen : darkestGreen, for: .normal)
                }
            }

            // 6. Floating action buttons (Audio, Recenter, Overview)
            if typeName.contains("FloatingButton") {
                if !_showReportFeedbackButton && (typeName.contains("Report") || typeName.contains("Feedback")) {
                    view.isHidden = true
                    return
                }
                view.backgroundColor = isDark ? darkSurface : UIColor(red: 234/255, green: 243/255, blue: 222/255, alpha: 1)
                (view as? UIButton)?.tintColor = isDark ? brandGreen : darkestGreen
            }

            // 7. Labels & Text Colors
            if let label = view as? UILabel {
                if typeName.contains("TimeRemaining") {
                    label.textColor = brandGreen
                } else {
                    label.textColor = textColor
                }
            }
            
            // 8. Close / Cancel / Dismiss buttons
            if let button = view as? UIButton {
                if button.title(for: .normal) == "Close" || button.title(for: .normal) == "Cancel" || typeName.contains("Dismiss") || typeName.contains("Cancel") {
                    button.setTitleColor(isDark ? brandGreen : textDark, for: .normal)
                    button.tintColor = isDark ? brandGreen : textDark
                }
            }
        }
    }
    
    private func applyToAllSubviews(of view: UIView, apply: (UIView) -> Void) {
        apply(view)
        for subview in view.subviews {
            applyToAllSubviews(of: subview, apply: apply)
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    
    func setNavigationOptions(wayPoints: [Waypoint]) {
        var mode: ProfileIdentifier = .automobileAvoidingTraffic
        
        if (_navigationMode == "cycling")
        {
            mode = .cycling
        }
        else if(_navigationMode == "driving")
        {
            mode = .automobile
        }
        else if(_navigationMode == "walking")
        {
            mode = .walking
        }
        let options = NavigationRouteOptions(waypoints: wayPoints, profileIdentifier: mode)
        
        if (_allowsUTurnAtWayPoints != nil)
        {
            options.allowsUTurnAtWaypoint = _allowsUTurnAtWayPoints!
        }
        
        options.distanceMeasurementSystem = _voiceUnits == "imperial" ? .imperial : .metric
        options.locale = Locale(identifier: _language)
        _options = options
    }
    
    func parseFlutterArguments(arguments: NSDictionary?) {
        _language = arguments?["language"] as? String ?? _language
        _voiceUnits = arguments?["units"] as? String ?? _voiceUnits
        _simulateRoute = arguments?["simulateRoute"] as? Bool ?? _simulateRoute
        _isOptimized = arguments?["isOptimized"] as? Bool ?? _isOptimized
        _allowsUTurnAtWayPoints = arguments?["allowsUTurnAtWayPoints"] as? Bool
        _navigationMode = arguments?["mode"] as? String ?? "drivingWithTraffic"
        _showReportFeedbackButton = arguments?["showReportFeedbackButton"] as? Bool ?? _showReportFeedbackButton
        _showEndOfRouteFeedback = arguments?["showEndOfRouteFeedback"] as? Bool ?? _showEndOfRouteFeedback
        _enableOnMapTapCallback = arguments?["enableOnMapTapCallback"] as? Bool ?? _enableOnMapTapCallback
        _mapStyleUrlDay = arguments?["mapStyleUrlDay"] as? String
        _mapStyleUrlNight = arguments?["mapStyleUrlNight"] as? String
        _isDarkTheme = arguments?["isDarkTheme"] as? Bool ?? _isDarkTheme
        _arrivalRadius = arguments?["arrivalRadius"] as? Double
        _zoom = arguments?["zoom"] as? Double ?? _zoom
        _bearing = arguments?["bearing"] as? Double ?? _bearing
        _tilt = arguments?["tilt"] as? Double ?? _tilt
        _animateBuildRoute = arguments?["animateBuildRoute"] as? Bool ?? _animateBuildRoute
        _longPressDestinationEnabled = arguments?["longPressDestinationEnabled"] as? Bool ?? _longPressDestinationEnabled
        _alternatives = arguments?["alternatives"] as? Bool ?? _alternatives
        
        let token = (arguments?["token"] as? String) ?? (Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String)
        if let token = token, !token.isEmpty, token != "$(MAPBOX_ACCESS_TOKEN)" {
            ResourceOptionsManager.default.resourceOptions.accessToken = token
        }
    }
    
    
    func continueNavigationWithWayPoints(wayPoints: [Waypoint])
    {
        _options?.waypoints = wayPoints
        Directions.shared.calculate(_options!) { [weak self](session, result) in
            guard let strongSelf = self else { return }
            switch result {
            case .failure(let error):
                strongSelf.sendEvent(eventType: MapBoxEventType.route_build_failed, data: error.localizedDescription)
            case .success(let response):
                strongSelf.sendEvent(eventType: MapBoxEventType.route_built, data: strongSelf.encodeRouteResponse(response: response))
                guard let routes = response.routes else { return }
                //TODO: if more than one route found, give user option to select one: DOES NOT WORK
                if(routes.count > 1 && strongSelf.ALLOW_ROUTE_SELECTION)
                {
                    //TODO: show map to select a specific route
                    
                }
                else
                {
                    strongSelf._navigationViewController?.navigationService.start()
                }
            }
        }
        
    }
    
    func endNavigation(result: FlutterResult?)
    {
        sendEvent(eventType: MapBoxEventType.navigation_finished)
        if(self._navigationViewController != nil)
        {
            self._navigationViewController?.navigationMapView?.removeRoutes()
            self._navigationViewController?.navigationService.endNavigation(feedback: nil)
            if(isEmbeddedNavigation)
            {
                self._navigationViewController?.view.removeFromSuperview()
                self._navigationViewController?.removeFromParent()
                self._navigationViewController = nil
            }
            else
            {
                self._navigationViewController?.dismiss(animated: true, completion: {
                    self._navigationViewController = nil
                    if(result != nil)
                    {
                        result!(true)
                    }
                })
            }
        }
        
    }
    
    func getLocationsFromFlutterArgument(arguments: NSDictionary?) -> [Location]? {
        
        var locations = [Location]()
        guard let oWayPoints = arguments?["wayPoints"] as? NSDictionary else {return nil}
        for item in oWayPoints as NSDictionary
        {
            let point = item.value as! NSDictionary
            guard let oName = point["Name"] as? String else {return nil }
            guard let oLatitude = point["Latitude"] as? Double else {return nil}
            guard let oLongitude = point["Longitude"] as? Double else {return nil}
            let oIsSilent = point["IsSilent"] as? Bool ?? false
            let order = point["Order"] as? Int
            let location = Location(name: oName, latitude: oLatitude, longitude: oLongitude, order: order,isSilent: oIsSilent)
            locations.append(location)
        }
        if(!_isOptimized)
        {
            //waypoints must be in the right order
            locations.sort(by: {$0.order ?? 0 < $1.order ?? 0})
        }
        return locations
    }
    
    func getLastKnownLocation() -> Waypoint
    {
        return Waypoint(coordinate: CLLocationCoordinate2D(latitude: _lastKnownLocation!.coordinate.latitude, longitude: _lastKnownLocation!.coordinate.longitude))
    }
    
    
    
    func sendEvent(eventType: MapBoxEventType, data: String = "")
    {
        let routeEvent = MapBoxRouteEvent(eventType: eventType, data: data)
        
        let jsonEncoder = JSONEncoder()
        let jsonData = try! jsonEncoder.encode(routeEvent)
        let eventJson = String(data: jsonData, encoding: String.Encoding.utf8)
        if(_eventSink != nil){
            _eventSink!(eventJson)
        }
        
    }
    
    func downloadOfflineRoute(arguments: NSDictionary?, flutterResult: @escaping FlutterResult)
    {
        /*
         // Create a directions client and store it as a property on the view controller.
         self.navigationDirections = NavigationDirections(credentials: Directions.shared.credentials)
         
         // Fetch available routing tile versions.
         _ = self.navigationDirections!.fetchAvailableOfflineVersions { (versions, error) in
         guard let version = versions?.first else { return }
         
         let coordinateBounds = CoordinateBounds(southWest: CLLocationCoordinate2DMake(0, 0), northEast: CLLocationCoordinate2DMake(1, 1))
         
         // Download tiles using the most recent version.
         _ = self.navigationDirections!.downloadTiles(in: coordinateBounds, version: version) { (url, response, error) in
         guard let url = url else {
         flutterResult(false)
         preconditionFailure("Unable to locate temporary file.")
         }
         
         guard let outputDirectoryURL = Bundle.mapboxCoreNavigation.suggestedTileURL(version: version) else {
         flutterResult(false)
         preconditionFailure("No suggested tile URL.")
         }
         try? FileManager.default.createDirectory(at: outputDirectoryURL, withIntermediateDirectories: true, attributes: nil)
         
         // Unpack downloaded routing tiles.
         NavigationDirections.unpackTilePack(at: url, outputDirectoryURL: outputDirectoryURL, progressHandler: { (totalBytes, bytesRemaining) in
         // Show unpacking progress.
         }, completionHandler: { (result, error) in
         // Configure the offline router with the output directory where the tiles have been unpacked.
         self.navigationDirections!.configureRouter(tilesURL: outputDirectoryURL) { (numberOfTiles) in
         // Completed, dismiss UI
         flutterResult(true)
         }
         })
         }
         }
         */
    }
    
    func encodeRouteResponse(response: RouteResponse) -> String {
        let routes = response.routes
        
        if routes != nil && !routes!.isEmpty {
            let jsonEncoder = JSONEncoder()
            let jsonData = try! jsonEncoder.encode(response.routes!)
            return String(data: jsonData, encoding: String.Encoding.utf8) ?? "{}"
        }
        
        return "{}"
    }
    
    //MARK: EventListener Delegates
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        _eventSink = events
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        _eventSink = nil
        return nil
    }
}


extension NavigationFactory : NavigationViewControllerDelegate {
    //MARK: NavigationViewController Delegates
    public func navigationViewController(_ navigationViewController: NavigationViewController, didUpdate progress: RouteProgress, with location: CLLocation, rawLocation: CLLocation) {
        _lastKnownLocation = location
        _distanceRemaining = progress.distanceRemaining
        _durationRemaining = progress.durationRemaining
        sendEvent(eventType: MapBoxEventType.navigation_running)
        
        let isDarkMode = _isDarkTheme || (_mapStyleUrlDay?.lowercased().contains("dark") == true)
        applyEpicThemeToNavVC(navigationViewController, isDark: isDarkMode)
        //_currentLegDescription =  progress.currentLeg.description
        if(_eventSink != nil)
        {
            let jsonEncoder = JSONEncoder()
            
            let progressEvent = MapBoxRouteProgressEvent(progress: progress)
            let progressEventJsonData = try! jsonEncoder.encode(progressEvent)
            let progressEventJson = String(data: progressEventJsonData, encoding: String.Encoding.ascii)
            
            _eventSink!(progressEventJson)
            
            if(progress.isFinalLeg && progress.currentLegProgress.userHasArrivedAtWaypoint && !_showEndOfRouteFeedback)
            {
                _eventSink = nil
            }
        }
    }
    
    public func navigationViewController(_ navigationViewController: NavigationViewController, didArriveAt waypoint: Waypoint) -> Bool {
        sendEvent(eventType: MapBoxEventType.on_arrival, data: "true")
        if(!_wayPoints.isEmpty && IsMultipleUniqueRoutes)
        {
            continueNavigationWithWayPoints(wayPoints: [getLastKnownLocation(), _wayPoints.remove(at: 0)])
            return false
        }
        
        return true
    }
    
    
    
    public func navigationViewControllerDidDismiss(_ navigationViewController: NavigationViewController, byCanceling canceled: Bool) {
        if(canceled)
        {
            sendEvent(eventType: MapBoxEventType.navigation_cancelled)
        }
        endNavigation(result: nil)
    }
    
    public func navigationViewController(_ navigationViewController: NavigationViewController, shouldRerouteFrom location: CLLocation) -> Bool {
        return _shouldReRoute
    }
    
    public func navigationViewController(_ navigationViewController: NavigationViewController, didSubmitArrivalFeedback feedback: EndOfRouteFeedback) {
        
        if(_eventSink != nil)
        {
            let jsonEncoder = JSONEncoder()
            
            let localFeedback = Feedback(rating: feedback.rating, comment: feedback.comment)
            let feedbackJsonData = try! jsonEncoder.encode(localFeedback)
            let feedbackJson = String(data: feedbackJsonData, encoding: String.Encoding.ascii)
            
            sendEvent(eventType: MapBoxEventType.navigation_finished, data: feedbackJson ?? "")
            
            _eventSink = nil
            
        }
    }
}
