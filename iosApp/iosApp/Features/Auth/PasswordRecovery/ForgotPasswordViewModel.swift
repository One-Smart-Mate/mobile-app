import Foundation
import Observation
import SharedLogic

enum ForgotPasswordStep: Int, CaseIterable {
    case email = 1
    case code = 2
    case password = 3
    case success

    var progressStep: Int { self == .success ? 3 : rawValue }
}

enum ForgotPasswordBanner: Equatable {
    case codeSent
    case invalidCode
    case expiredCode
    case tooManyAttempts
    case noConnection
    case requestFailed

    var type: AnatomyBannerType {
        self == .codeSent ? .success : .error
    }
}

@MainActor
@Observable
final class ForgotPasswordViewModel {
    private(set) var step: ForgotPasswordStep = .email
    private(set) var email: String
    private(set) var code = ""
    private(set) var password = ""
    private(set) var passwordConfirmation = ""
    private(set) var emailError: PasswordRecoveryValidationError?
    private(set) var codeError: PasswordRecoveryValidationError?
    private(set) var passwordError: PasswordRecoveryValidationError?
    private(set) var confirmationError: PasswordRecoveryValidationError?
    private(set) var banner: ForgotPasswordBanner?
    private(set) var isLoading = false
    private(set) var resendSeconds = 0

    private let controller: IosPasswordRecoveryController?
    private var resendCountdownTask: Task<Void, Never>?

    init(
        initialEmail: String = "",
        controller: IosPasswordRecoveryController? = nil
    ) {
        email = initialEmail
        self.controller = controller
    }

    func updateEmail(_ value: String) {
        email = value
        emailError = nil
        banner = nil
    }

    func updateCode(_ value: String) {
        code = controller?.normalizeCode(value: value)
            ?? String(value.filter { $0.isLetter || $0.isNumber }.uppercased().prefix(6))
        codeError = nil
        banner = nil
    }

    func updatePassword(_ value: String) {
        password = value
        passwordError = nil
        confirmationError = nil
        banner = nil
    }

    func updatePasswordConfirmation(_ value: String) {
        passwordConfirmation = value
        confirmationError = nil
        banner = nil
    }

    func dismissBanner() {
        banner = nil
    }

    func submit() async -> Bool {
        guard !isLoading else { return false }
        switch step {
        case .email:
            await requestCode(moveToCodeStep: true)
            return false
        case .code:
            await verifyCode()
            return false
        case .password:
            await resetPassword()
            return false
        case .success:
            return true
        }
    }

    func resendCode() async {
        guard step == .code, resendSeconds == 0, !isLoading else { return }
        await requestCode(moveToCodeStep: false)
    }

    /// Returns true when the navigation container should close this flow.
    func back() -> Bool {
        guard !isLoading else { return false }
        switch step {
        case .email, .success:
            return true
        case .code:
            resendCountdownTask?.cancel()
            resendCountdownTask = nil
            step = .email
            code = ""
            codeError = nil
            banner = nil
            resendSeconds = 0
            return false
        case .password:
            step = .code
            password = ""
            passwordConfirmation = ""
            passwordError = nil
            confirmationError = nil
            banner = nil
            return false
        }
    }

    func stop() {
        resendCountdownTask?.cancel()
        resendCountdownTask = nil
    }

    private func requestCode(moveToCodeStep: Bool) async {
        guard let controller else {
            banner = .requestFailed
            return
        }
        isLoading = true
        emailError = nil
        banner = nil
        do {
            let outcome = try await controller.requestCode(email: email)
            isLoading = false
            if outcome.succeeded {
                email = outcome.email ?? email
                if moveToCodeStep {
                    step = .code
                    code = ""
                }
                codeError = nil
                banner = .codeSent
                startResendCountdown()
            } else {
                applyRequestFailure(outcome)
            }
        } catch is CancellationError {
            isLoading = false
        } catch {
            isLoading = false
            banner = .requestFailed
        }
    }

    private func verifyCode() async {
        guard let controller else {
            banner = .requestFailed
            return
        }
        isLoading = true
        codeError = nil
        banner = nil
        do {
            let outcome = try await controller.verifyCode(email: email, resetCode: code)
            isLoading = false
            if outcome.succeeded {
                email = outcome.email ?? email
                code = outcome.resetCode ?? code
                step = .password
            } else {
                applyCodeFailure(outcome)
            }
        } catch is CancellationError {
            isLoading = false
        } catch {
            isLoading = false
            banner = .requestFailed
        }
    }

    private func resetPassword() async {
        guard let controller else {
            banner = .requestFailed
            return
        }
        isLoading = true
        passwordError = nil
        confirmationError = nil
        banner = nil
        do {
            let outcome = try await controller.resetPassword(
                email: email,
                resetCode: code,
                newPassword: password,
                confirmation: passwordConfirmation
            )
            isLoading = false
            if outcome.succeeded {
                resendCountdownTask?.cancel()
                resendCountdownTask = nil
                resendSeconds = 0
                password = ""
                passwordConfirmation = ""
                step = .success
            } else {
                applyPasswordFailure(outcome)
            }
        } catch is CancellationError {
            isLoading = false
        } catch {
            isLoading = false
            banner = .requestFailed
        }
    }

    private func applyRequestFailure(_ outcome: IosPasswordRecoveryOutcome) {
        if let validation = outcome.validationError {
            emailError = validation
        } else {
            banner = banner(for: outcome.failure)
        }
    }

    private func applyCodeFailure(_ outcome: IosPasswordRecoveryOutcome) {
        if let validation = outcome.validationError {
            codeError = validation
        } else {
            banner = banner(for: outcome.failure)
        }
    }

    private func applyPasswordFailure(_ outcome: IosPasswordRecoveryOutcome) {
        if let validation = outcome.validationError {
            switch validation {
            case .confirmationRequired, .passwordsDoNotMatch:
                confirmationError = validation
            case .passwordRequired, .passwordTooShort:
                passwordError = validation
            case .codeInvalid:
                codeError = validation
            default:
                emailError = validation
            }
        } else {
            banner = banner(for: outcome.failure)
        }
    }

    private func banner(for failure: IosPasswordRecoveryFailure?) -> ForgotPasswordBanner {
        switch failure {
        case .tooManyAttempts: .tooManyAttempts
        case .noConnection: .noConnection
        case .expiredCode: .expiredCode
        case .invalidCode: .invalidCode
        case .requestFailed, nil: .requestFailed
        default: .requestFailed
        }
    }

    private func startResendCountdown() {
        resendCountdownTask?.cancel()
        resendCountdownTask = Task { [weak self] in
            guard let self else { return }
            for remaining in stride(from: 60, through: 1, by: -1) {
                guard !Task.isCancelled else { return }
                resendSeconds = remaining
                try? await Task.sleep(for: .seconds(1))
            }
            guard !Task.isCancelled else { return }
            resendSeconds = 0
        }
    }
}
