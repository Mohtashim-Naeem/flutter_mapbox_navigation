import MapboxMaps
import MapboxDirections
import MapboxCoreNavigation
import MapboxNavigation

class CustomNightStyle: NightStyle {

    required init() {
        super.init()
        initStyle()
    }

    init(url: String?){
        super.init()
        initStyle()
        if(url != nil)
        {
            mapStyleURL = URL(string: url!) ?? URL(string: StyleURI.navigationNight.rawValue)!
            previewMapStyleURL = mapStyleURL
        }
    }

    func initStyle()
    {
        // Use a custom map style.
        mapStyleURL = URL(string: StyleURI.navigationNight.rawValue)!
        previewMapStyleURL = mapStyleURL

        // Specify that the style should be used during the day.
        styleType = .night
    }

    override func apply() {
        super.apply()
        let darkSurface = UIColor(red: 16.0 / 255.0, green: 43.0 / 255.0, blue: 13.0 / 255.0, alpha: 1.0)
        let maneuverBg = UIColor(red: 18.0 / 255.0, green: 31.0 / 255.0, blue: 17.0 / 255.0, alpha: 1.0)
        let brandGreen = UIColor(red: 97.0 / 255.0, green: 203.0 / 255.0, blue: 8.0 / 255.0, alpha: 1.0)
        let textPrimary = UIColor(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, alpha: 1.0)

        TopBannerView.appearance().backgroundColor = maneuverBg
        BottomBannerView.appearance().backgroundColor = darkSurface
        BottomPaddingView.appearance().backgroundColor = darkSurface
        TimeRemainingLabel.appearance().textColor = brandGreen
        DistanceRemainingLabel.appearance().textColor = textPrimary
        ArrivalTimeLabel.appearance().textColor = textPrimary
        
        PrimaryLabel.appearance().textColor = textPrimary
        SecondaryLabel.appearance().textColor = textPrimary
        
        ManeuverView.appearance().primaryColor = textPrimary
        ManeuverView.appearance().secondaryColor = textPrimary
    }
}
