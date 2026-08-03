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
        let darkBg = UIColor(red: 11.0 / 255.0, green: 39.0 / 255.0, blue: 0.0 / 255.0, alpha: 1.0)
        let brandGreen = UIColor(red: 97.0 / 255.0, green: 203.0 / 255.0, blue: 8.0 / 255.0, alpha: 1.0)

        BottomBannerView.appearance().backgroundColor = darkBg
        BottomPaddingView.appearance().backgroundColor = darkBg
        TimeRemainingLabel.appearance().textColor = brandGreen
        DistanceRemainingLabel.appearance().textColor = brandGreen
        DateLabel.appearance().textColor = brandGreen
    }
}
