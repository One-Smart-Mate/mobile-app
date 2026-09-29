import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class LoginViewModel {
    struct Credentials: Sendable {
        let email: String
        let password: String
    }

    enum EmailError {
        case required
        case invalid
    }

    enum PasswordError {
        case required
        case tooShort
    }

    typealias LoginHandler = @MainActor (Credentials) async throws -> Void
    typealias PasswordResetHandler = @MainActor (String) -> Void
    typealias EnvironmentChangeHandler = @MainActor (APIEnvironment) -> Void

    private(set) var email: String
    private(set) var password: String
    private(set) var emailError: EmailError?
    private(set) var passwordError: PasswordError?
    private(set) var bannerMessage: String?
    private(set) var isLoading = false
    private(set) var currentEnvironment: APIEnvironment

    private let loginHandler: LoginHandler
    private let passwordResetHandler: PasswordResetHandler
    private let environmentChangeHandler: EnvironmentChangeHandler
    private let emailValidator = EmailAddressValidator()

    init(
        email: String = "",
        password: String = "",
        emailError: EmailError? = nil,
        passwordError: PasswordError? = nil,
        bannerMessage: String? = nil,
        loginHandler: @escaping LoginHandler = { _ in },
        passwordResetHandler: @escaping PasswordResetHandler = { _ in },
        initialEnvironment: APIEnvironment = AppConfiguration.selectedAPIEnvironment,
        environmentChangeHandler: @escaping EnvironmentChangeHandler = { _ in }
    ) {
        self.email = email
        self.password = password
        self.emailError = emailError
        self.passwordError = passwordError
        self.bannerMessage = bannerMessage
        self.loginHandler = loginHandler
        self.passwordResetHandler = passwordResetHandler
        currentEnvironment = initialEnvironment
        self.environmentChangeHandler = environmentChangeHandler
    }

    func updateEmail(_ value: String) {
        email = value
        emailError = nil
        bannerMessage = nil
    }

    func updatePassword(_ value: String) {
        password = value
        passwordError = nil
        bannerMessage = nil
    }

    func dismissBanner() {
        bannerMessage = nil
    }

    func requestPasswordReset() {
        passwordResetHandler(normalizedEmail)
    }

    func selectEnvironment(_ environment: APIEnvironment) {
        guard !isLoading else { return }
        environmentChangeHandler(environment)
        currentEnvironment = environment
        bannerMessage = nil
    }

    @discardableResult
    func logIn() async -> Bool {
        guard !isLoading else {
            return false
        }

        guard validate() else {
            return false
        }

        isLoading = true
        bannerMessage = nil

        defer {
            isLoading = false
        }

        do {
            try Task.checkCancellation()
            try await loginHandler(
                Credentials(email: normalizedEmail, password: password)
            )
            try Task.checkCancellation()
            return true
        } catch is CancellationError {
            return false
        } catch {
            let message = error.localizedDescription.trimmingCharacters(
                in: .whitespacesAndNewlines
            )
            bannerMessage = message.isEmpty
                ? String(localized: AppStrings.Login.genericError)
                : message
            return false
        }
    }

    private var normalizedEmail: String {
        emailValidator.normalize(value: email)
    }

    private func validate() -> Bool {
        if normalizedEmail.isEmpty {
            emailError = .required
        } else if !emailValidator.isValid(value: normalizedEmail) {
            emailError = .invalid
        } else {
            emailError = nil
        }

        if password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            passwordError = .required
        } else if password.trimmingCharacters(in: .whitespacesAndNewlines).count < 8 {
            passwordError = .tooShort
        } else {
            passwordError = nil
        }

        return emailError == nil && passwordError == nil
    }

}
