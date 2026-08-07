import MapboxMaps
import MapboxNavigation
import MapboxMaps

class CustomDayStyle: DayStyle {

    required init() {
        super.init()
        initStyle()
    }

    init(url: String?){
        super.init()
        initStyle()
        if(url != nil)
        {
            mapStyleURL = URL(string: url!) ?? URL(string: StyleURI.navigationDay.rawValue)!
            previewMapStyleURL = mapStyleURL
        }
    }

    func initStyle()
    {
        // Use a custom map style.
        mapStyleURL = URL(string: StyleURI.navigationDay.rawValue)!
        previewMapStyleURL = mapStyleURL

        // Specify that the style should be used during the day.
        styleType = .day
    }

    override func apply() {
        super.apply()
        let lightSurface = UIColor(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, alpha: 1.0)
        let maneuverBg = UIColor(red: 249.0 / 255.0, green: 250.0 / 255.0, blue: 249.0 / 255.0, alpha: 1.0)
        let brandGreen = UIColor(red: 97.0 / 255.0, green: 203.0 / 255.0, blue: 8.0 / 255.0, alpha: 1.0)
        let textPrimary = UIColor(red: 15.0 / 255.0, green: 18.0 / 255.0, blue: 16.0 / 255.0, alpha: 1.0)

        TopBannerView.appearance().backgroundColor = maneuverBg
        BottomBannerView.appearance().backgroundColor = lightSurface
        BottomPaddingView.appearance().backgroundColor = lightSurface
        TimeRemainingLabel.appearance().textColor = brandGreen
        DistanceRemainingLabel.appearance().textColor = textPrimary
        ArrivalTimeLabel.appearance().textColor = textPrimary
        
        PrimaryLabel.appearance().textColor = textPrimary
        SecondaryLabel.appearance().textColor = textPrimary
        InstructionLabel.appearance().textColor = textPrimary
        TitleLabel.appearance().textColor = textPrimary
        SubtitleLabel.appearance().textColor = textPrimary
        
        ManeuverView.appearance().primaryColor = textPrimary
        ManeuverView.appearance().secondaryColor = textPrimary
        
        // Floating action buttons (Light theme)
        if let floatingButtonClass = NSClassFromString("MapboxNavigation.FloatingButton") as? UIAppearanceContainer.Type {
            UIButton.appearance(whenContainedInInstancesOf: [floatingButtonClass]).backgroundColor = UIColor(red: 234.0 / 255.0, green: 243.0 / 255.0, blue: 222.0 / 255.0, alpha: 1.0)
            UIButton.appearance(whenContainedInInstancesOf: [floatingButtonClass]).tintColor = UIColor(red: 11.0 / 255.0, green: 39.0 / 255.0, blue: 0.0 / 255.0, alpha: 1.0)
        }
    }
}
