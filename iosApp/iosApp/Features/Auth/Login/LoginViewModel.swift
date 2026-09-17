import Foundation
import Observation

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
    }

    typealias LoginHandler = @MainActor (Credentials) async throws -> Void
    typealias PasswordResetHandler = @MainActor (String) -> Void

    private(set) var email: String
    private(set) var password: String
    private(set) var emailError: EmailError?
    private(set) var passwordError: PasswordError?
    private(set) var bannerMessage: String?
    private(set) var isLoading = false

    private let loginHandler: LoginHandler
    private let passwordResetHandler: PasswordResetHandler

    init(
        email: String = "",
        password: String = "",
        emailError: EmailError? = nil,
        passwordError: PasswordError? = nil,
        bannerMessage: String? = nil,
        loginHandler: @escaping LoginHandler = { _ in },
        passwordResetHandler: @escaping PasswordResetHandler = { _ in }
    ) {
        self.email = email
        self.password = password
        self.emailError = emailError
        self.passwordError = passwordError
        self.bannerMessage = bannerMessage
        self.loginHandler = loginHandler
        self.passwordResetHandler = passwordResetHandler
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
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func validate() -> Bool {
        if normalizedEmail.isEmpty {
            emailError = .required
        } else if !isValidEmail(normalizedEmail) {
            emailError = .invalid
        } else {
            emailError = nil
        }

        passwordError = password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? .required
            : nil

        return emailError == nil && passwordError == nil
    }

    private func isValidEmail(_ value: String) -> Bool {
        let pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$"
        return value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }
}
