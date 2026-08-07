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
        if let urlString = url, let validUrl = URL(string: urlString) {
            mapStyleURL = validUrl
            previewMapStyleURL = validUrl
        } else {
            mapStyleURL = URL(string: StyleURI.navigationNight.rawValue)!
            previewMapStyleURL = mapStyleURL
        }
        styleType = .night
    }

    func initStyle()
    {
        mapStyleURL = URL(string: StyleURI.navigationNight.rawValue)!
        previewMapStyleURL = mapStyleURL
        styleType = .night
    }

    override func apply() {
        super.apply()
        let darkSurface = UIColor(red: 24.0 / 255.0, green: 56.0 / 255.0, blue: 20.0 / 255.0, alpha: 1.0)
        let maneuverBg = UIColor(red: 11.0 / 255.0, green: 39.0 / 255.0, blue: 0.0 / 255.0, alpha: 1.0)
        let brandGreen = UIColor(red: 97.0 / 255.0, green: 203.0 / 255.0, blue: 8.0 / 255.0, alpha: 1.0)
        let textPrimary = UIColor(red: 255.0 / 255.0, green: 255.0 / 255.0, blue: 255.0 / 255.0, alpha: 1.0)

        InstructionsBannerView.appearance().backgroundColor = maneuverBg
        NextBannerView.appearance().backgroundColor = maneuverBg
        StepInstructionsView.appearance().backgroundColor = maneuverBg
        TopBannerView.appearance().backgroundColor = maneuverBg
        BottomBannerView.appearance().backgroundColor = darkSurface
        BottomPaddingView.appearance().backgroundColor = darkSurface
        
        StepsBackgroundView.appearance().backgroundColor = maneuverBg
        StepTableViewCell.appearance().backgroundColor = maneuverBg
        
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
        
        ResumeButton.appearance().backgroundColor = darkSurface
        ResumeButton.appearance().tintColor = brandGreen
        DismissButton.appearance().tintColor = brandGreen
        CancelButton.appearance().tintColor = brandGreen
        
        // Floating action buttons (Dark theme)
        if let floatingButtonClass = NSClassFromString("MapboxNavigation.FloatingButton") as? UIButton.Type {
            floatingButtonClass.appearance().backgroundColor = darkSurface
            floatingButtonClass.appearance().tintColor = brandGreen
        }
    }
}
