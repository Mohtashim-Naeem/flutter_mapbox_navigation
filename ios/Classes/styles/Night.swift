import UIKit
import MapboxMaps
import MapboxDirections
import MapboxCoreNavigation
import MapboxNavigation

class CustomNightStyle: NightStyle {

    /// Must match `MapboxNavigation.Style.traitCollection` exactly.
    ///
    /// Every appearance proxy inside DayStyle/NightStyle is registered with
    /// `appearance(for: traitCollection, ...)`. A trait-scoped proxy always outranks the
    /// plain `appearance()` proxy, so overrides written as `Foo.appearance().bar = ...`
    /// are silently ignored on device — which is why the steps list kept the stock SDK
    /// colours while the top banner looked correct.
    private static let epicTraitCollection = UITraitCollection(traitsFrom: [
        UITraitCollection(userInterfaceIdiom: .phone),
        UITraitCollection(userInterfaceIdiom: .pad),
    ])

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

        let traits = CustomNightStyle.epicTraitCollection

        let darkSurface = UIColor(red: 24.0 / 255.0, green: 56.0 / 255.0, blue: 20.0 / 255.0, alpha: 1.0)
        let maneuverBg = UIColor(red: 11.0 / 255.0, green: 39.0 / 255.0, blue: 0.0 / 255.0, alpha: 1.0)
        let brandGreen = UIColor(red: 97.0 / 255.0, green: 203.0 / 255.0, blue: 8.0 / 255.0, alpha: 1.0)
        let textPrimary = UIColor.white
        let textSecondary = UIColor.white.withAlphaComponent(0.7)

        // --- Background colors ---
        InstructionsBannerView.appearance(for: traits).backgroundColor = maneuverBg
        NextBannerView.appearance(for: traits).backgroundColor = maneuverBg
        StepInstructionsView.appearance(for: traits).backgroundColor = maneuverBg
        TopBannerView.appearance(for: traits).backgroundColor = maneuverBg
        BottomBannerView.appearance(for: traits).backgroundColor = darkSurface
        BottomPaddingView.appearance(for: traits).backgroundColor = darkSurface

        StepsBackgroundView.appearance(for: traits).backgroundColor = maneuverBg
        StepTableViewCell.appearance(for: traits).backgroundColor = maneuverBg

        // --- Row separators inside the steps list ---
        SeparatorView.appearance(for: traits).backgroundColor = UIColor.white.withAlphaComponent(0.15)

        // --- Bottom banner labels ---
        TimeRemainingLabel.appearance(for: traits).trafficUnknownColor = brandGreen
        TimeRemainingLabel.appearance(for: traits).trafficLowColor = brandGreen
        TimeRemainingLabel.appearance(for: traits).trafficModerateColor = brandGreen
        TimeRemainingLabel.appearance(for: traits).trafficHeavyColor = brandGreen
        TimeRemainingLabel.appearance(for: traits).trafficSevereColor = brandGreen
        DistanceRemainingLabel.appearance(for: traits).normalTextColor = textPrimary
        ArrivalTimeLabel.appearance(for: traits).normalTextColor = textPrimary

        // --- Instruction labels (use normalTextColor — SDK builds attributedText from this) ---
        PrimaryLabel.appearance(for: traits).normalTextColor = textPrimary
        SecondaryLabel.appearance(for: traits).normalTextColor = textPrimary
        InstructionLabel.appearance(for: traits).normalTextColor = textPrimary
        TitleLabel.appearance(for: traits).normalTextColor = textPrimary
        SubtitleLabel.appearance(for: traits).normalTextColor = textPrimary

        // --- Top banner: labels scoped to InstructionsBannerView ---
        PrimaryLabel.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).normalTextColor = textPrimary
        SecondaryLabel.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).normalTextColor = textSecondary

        // --- Steps list: labels scoped to StepInstructionsView ---
        PrimaryLabel.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).normalTextColor = textPrimary
        SecondaryLabel.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).normalTextColor = textSecondary

        // --- Distance labels (use valueTextColor / unitTextColor, NOT textColor) ---
        DistanceLabel.appearance(for: traits).valueTextColor = textPrimary
        DistanceLabel.appearance(for: traits).unitTextColor = textSecondary
        DistanceLabel.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).valueTextColor = textPrimary
        DistanceLabel.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).unitTextColor = textSecondary
        DistanceLabel.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).valueTextColor = textPrimary
        DistanceLabel.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).unitTextColor = textSecondary

        // --- Maneuver arrows ---
        ManeuverView.appearance(for: traits).primaryColor = textPrimary
        ManeuverView.appearance(for: traits).secondaryColor = textSecondary
        ManeuverView.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).primaryColor = textPrimary
        ManeuverView.appearance(for: traits, whenContainedInInstancesOf: [InstructionsBannerView.self]).secondaryColor = textSecondary
        ManeuverView.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).primaryColor = textPrimary
        ManeuverView.appearance(for: traits, whenContainedInInstancesOf: [StepInstructionsView.self]).secondaryColor = textSecondary

        // --- Buttons ---
        ResumeButton.appearance(for: traits).backgroundColor = darkSurface
        ResumeButton.appearance(for: traits).tintColor = brandGreen
        // "Close" bar under the steps list
        DismissButton.appearance(for: traits).backgroundColor = darkSurface
        DismissButton.appearance(for: traits).textColor = brandGreen
        DismissButton.appearance(for: traits).tintColor = brandGreen
        CancelButton.appearance(for: traits).tintColor = brandGreen

        // Floating action buttons (Dark theme)
        if let floatingButtonClass = NSClassFromString("MapboxNavigation.FloatingButton") as? UIButton.Type {
            floatingButtonClass.appearance(for: traits).backgroundColor = darkSurface
            floatingButtonClass.appearance(for: traits).tintColor = brandGreen
        }
    }
}
