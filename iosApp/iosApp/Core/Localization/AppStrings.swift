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
        static let passwordTooShort: LocalizedStringResource = "login.password.error.too_short"
        static let forgotPassword: LocalizedStringResource = "login.forgot_password"
        static let submit: LocalizedStringResource = "login.submit"
        static let secureAccess: LocalizedStringResource = "login.secure_access"
        static let genericError: LocalizedStringResource = "login.error.generic"
    }

    enum Session {
        static let loading: LocalizedStringResource = "session.loading"
    }

    enum Home {
        static let greeting: LocalizedStringResource = "home.greeting"
        static let quickActions: LocalizedStringResource = "home.quick_actions"
        static let newNote: LocalizedStringResource = "home.action.new_note"
        static let scanQR: LocalizedStringResource = "home.action.scan_qr"
        static let fastPassword: LocalizedStringResource = "home.action.fast_password"
        static let notes: LocalizedStringResource = "home.action.notes"
    }

    enum CatalogSync {
        static let title: LocalizedStringResource = "catalog_sync.title"
        static let starting: LocalizedStringResource = "catalog_sync.starting"
        static let waiting: LocalizedStringResource = "catalog_sync.waiting"
        static let failed: LocalizedStringResource = "catalog_sync.failed"
        static let genericError: LocalizedStringResource = "catalog_sync.error.generic"
        static let cardTypes: LocalizedStringResource = "catalog_sync.card_types"
        static let preclassifiers: LocalizedStringResource = "catalog_sync.preclassifiers"
        static let priorities: LocalizedStringResource = "catalog_sync.priorities"
        static let levels: LocalizedStringResource = "catalog_sync.levels"
        static let employees: LocalizedStringResource = "catalog_sync.employees"
    }

    enum Network {
        static let wifiConnected: LocalizedStringResource = "network.wifi.connected"
        static let wifiNoInternet: LocalizedStringResource = "network.wifi.no_internet"
        static let mobileConnected: LocalizedStringResource = "network.mobile.connected"
        static let mobileNoInternet: LocalizedStringResource = "network.mobile.no_internet"
        static let offline: LocalizedStringResource = "network.offline"
    }

    enum Navigation {
        static let home: LocalizedStringResource = "navigation.home"
        static let notes: LocalizedStringResource = "navigation.notes"
        static let settings: LocalizedStringResource = "navigation.settings"
    }

    enum Notes {
        static let title: LocalizedStringResource = "notes.title"
    }

    enum Settings {
        static let title: LocalizedStringResource = "settings.title"
    }

    enum CreateNote {
        static let title: LocalizedStringResource = "create_note.title"
    }
}
