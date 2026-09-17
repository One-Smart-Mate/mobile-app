import Foundation

enum AppStrings {
    enum Accessibility {
        static let dismiss: LocalizedStringResource = "accessibility.dismiss"
        static let hidePassword: LocalizedStringResource = "accessibility.password.hide"
        static let showPassword: LocalizedStringResource = "accessibility.password.show"
        static let loading: LocalizedStringResource = "accessibility.loading"
        static let bannerError: LocalizedStringResource = "accessibility.banner.error"
        static let bannerWarning: LocalizedStringResource = "accessibility.banner.warning"
        static let bannerInfo: LocalizedStringResource = "accessibility.banner.info"
        static let bannerSuccess: LocalizedStringResource = "accessibility.banner.success"
    }

    enum Login {
        static let brandName: LocalizedStringResource = "login.brand.name"
        static let brandTagline: LocalizedStringResource = "login.brand.tagline"
        static let title: LocalizedStringResource = "login.title"
        static let subtitle: LocalizedStringResource = "login.subtitle"
        static let emailLabel: LocalizedStringResource = "login.email.label"
        static let emailPlaceholder: LocalizedStringResource = "login.email.placeholder"
        static let passwordLabel: LocalizedStringResource = "login.password.label"
        static let passwordPlaceholder: LocalizedStringResource = "login.password.placeholder"
        static let emailRequired: LocalizedStringResource = "login.email.error.required"
        static let emailInvalid: LocalizedStringResource = "login.email.error.invalid"
        static let passwordRequired: LocalizedStringResource = "login.password.error.required"
        static let forgotPassword: LocalizedStringResource = "login.forgot_password"
        static let submit: LocalizedStringResource = "login.submit"
        static let secureAccess: LocalizedStringResource = "login.secure_access"
        static let genericError: LocalizedStringResource = "login.error.generic"
    }
}
