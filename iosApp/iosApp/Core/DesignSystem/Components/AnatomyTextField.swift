import SwiftUI

struct AnatomyTextField: View {
    @Binding var text: String

    let label: LocalizedStringResource
    let placeholder: LocalizedStringResource

    var supportingText: String?
    var isError = false
    var leadingSystemImage: String?
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .sentences
    var autocorrectionDisabled = false
    var submitLabel: SubmitLabel = .done
    var focus: Binding<Bool>?
    var autoFocus = false
    var onSubmit: () -> Void = {}

    @FocusState private var isFocused: Bool

    var body: some View {
        AnatomyFieldChrome(
            label: label,
            supportingText: supportingText,
            isError: isError,
            isFocused: isFocused,
            leadingSystemImage: leadingSystemImage
        ) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    prompt
                        .allowsHitTesting(false)
                }

                TextField("", text: $text)
                    .textFieldStyle(.plain)
                    .foregroundStyle(Color.osmOnSurface)
                    .tint(.osmPrimary)
            }
            .focused($isFocused)
            .keyboardType(keyboardType)
            .textContentType(textContentType)
            .textInputAutocapitalization(capitalization)
            .autocorrectionDisabled(autocorrectionDisabled)
            .submitLabel(submitLabel)
            .onSubmit(onSubmit)
            .accessibilityLabel(Text(label))
        } trailing: {
            EmptyView()
        }
        .task {
            if autoFocus || focus?.wrappedValue == true {
                await Task.yield()
                isFocused = true
            }
        }
        .onChange(of: focus?.wrappedValue) { _, newValue in
            guard let newValue, newValue != isFocused else {
                return
            }
            isFocused = newValue
        }
        .onChange(of: isFocused) { _, newValue in
            guard focus?.wrappedValue != newValue else {
                return
            }
            focus?.wrappedValue = newValue
        }
    }

    private var prompt: Text {
        Text(verbatim: String(localized: placeholder))
            .foregroundStyle(Color.osmOnSurfaceVariant.opacity(0.66))
    }
}

struct AnatomyPasswordField: View {
    @Binding var text: String

    let label: LocalizedStringResource
    let placeholder: LocalizedStringResource

    var supportingText: String?
    var isError = false
    var submitLabel: SubmitLabel = .done
    var focus: Binding<Bool>?
    var autoFocus = false
    var onSubmit: () -> Void = {}

    @Environment(\.isEnabled) private var isEnabled
    @FocusState private var isFocused: Bool
    @State private var isPasswordVisible = false

    var body: some View {
        AnatomyFieldChrome(
            label: label,
            supportingText: supportingText,
            isError: isError,
            isFocused: isFocused,
            leadingSystemImage: "lock"
        ) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    prompt
                        .allowsHitTesting(false)
                }

                Group {
                    if isPasswordVisible {
                        TextField("", text: $text)
                    } else {
                        SecureField("", text: $text)
                    }
                }
                .textFieldStyle(.plain)
                .foregroundStyle(Color.osmOnSurface)
                .tint(.osmPrimary)
            }
            .focused($isFocused)
            .textContentType(.password)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(submitLabel)
            .onSubmit(onSubmit)
            .accessibilityLabel(Text(label))
        } trailing: {
            Button {
                isPasswordVisible.toggle()
                isFocused = true
            } label: {
                Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .foregroundStyle(trailingColor)
            .disabled(!isEnabled)
            .accessibilityLabel(
                Text(
                    isPasswordVisible
                        ? AppStrings.Accessibility.hidePassword
                        : AppStrings.Accessibility.showPassword
                )
            )
        }
        .privacySensitive()
        .task {
            if autoFocus || focus?.wrappedValue == true {
                await Task.yield()
                isFocused = true
            }
        }
        .onChange(of: focus?.wrappedValue) { _, newValue in
            guard let newValue, newValue != isFocused else {
                return
            }
            isFocused = newValue
        }
        .onChange(of: isFocused) { _, newValue in
            guard focus?.wrappedValue != newValue else {
                return
            }
            focus?.wrappedValue = newValue
        }
    }

    private var prompt: Text {
        Text(verbatim: String(localized: placeholder))
            .foregroundStyle(Color.osmOnSurfaceVariant.opacity(0.66))
    }

    private var trailingColor: Color {
        if !isEnabled {
            return .osmOnSurfaceVariant.opacity(0.4)
        }
        if isError {
            return .osmError
        }
        return isFocused ? .osmPrimary : .osmOnSurfaceVariant
    }
}

private struct AnatomyFieldChrome<Content: View, Trailing: View>: View {
    @Environment(\.isEnabled) private var isEnabled
    @ScaledMetric(relativeTo: .body) private var controlHeight: CGFloat = 52

    let label: LocalizedStringResource
    let supportingText: String?
    let isError: Bool
    let isFocused: Bool
    let leadingSystemImage: String?
    @ViewBuilder let content: Content
    @ViewBuilder let trailing: Trailing

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(labelColor)
                .padding(.leading, OSMSpacing.xxs)

            HStack(spacing: 0) {
                if let leadingSystemImage {
                    ZStack {
                        Color.osmOnSurface.opacity(0.025)
                        Image(systemName: leadingSystemImage)
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(iconColor)
                    }
                    .frame(width: 44)
                    .frame(maxHeight: .infinity)
                }

                content
                    .font(OSMTypography.body)
                    .foregroundStyle(contentColor)
                    .padding(.horizontal, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)

                trailing
                    .padding(.trailing, OSMSpacing.xxs)
            }
            .frame(height: controlHeight)
            .background(containerColor)
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(accentColor)
                    .frame(width: 4)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .animation(.easeInOut(duration: 0.18), value: isFocused)
            .animation(.easeInOut(duration: 0.18), value: isError)

            if let supportingText {
                Text(verbatim: supportingText)
                    .font(OSMTypography.caption)
                    .foregroundStyle(isError ? Color.osmError : .osmOnSurfaceVariant)
                    .padding(.leading, OSMSpacing.xxs)
                    .transition(.opacity)
            }
        }
    }

    private var containerColor: Color {
        guard isEnabled else {
            return .osmSurfaceVariant.opacity(0.55)
        }
        if isError {
            return .osmErrorContainer.opacity(0.72)
        }
        if isFocused {
            return .osmPrimaryContainer.opacity(0.7)
        }
        return .osmSurfaceVariant
    }

    private var accentColor: Color {
        guard isEnabled else {
            return .clear
        }
        if isError {
            return .osmError
        }
        return isFocused ? .osmPrimary : .clear
    }

    private var labelColor: Color {
        guard isEnabled else {
            return .osmOnSurfaceVariant.opacity(0.55)
        }
        if isError {
            return .osmError
        }
        return isFocused ? .osmPrimary : .osmOnSurface
    }

    private var contentColor: Color {
        isEnabled ? .osmOnSurface : .osmOnSurfaceVariant.opacity(0.55)
    }

    private var iconColor: Color {
        guard isEnabled else {
            return .osmOnSurfaceVariant.opacity(0.4)
        }
        if isError {
            return .osmError
        }
        return isFocused ? .osmPrimary : .osmOnSurfaceVariant
    }
}

private struct AnatomyTextFieldPreview: View {
    @State private var email = "carlos@empresa.com"
    @State private var password = "password"

    var body: some View {
        AnatomyPreviewCanvas {
            VStack(spacing: OSMSpacing.md) {
                AnatomyTextField(
                    text: $email,
                    label: "Correo electrónico",
                    placeholder: "nombre@empresa.com",
                    leadingSystemImage: "envelope"
                )
                AnatomyPasswordField(
                    text: $password,
                    label: "Contraseña",
                    placeholder: "Ingresa tu contraseña"
                )
                AnatomyTextField(
                    text: .constant("correo-invalido"),
                    label: "Correo electrónico",
                    placeholder: "nombre@empresa.com",
                    supportingText: "Correo no válido",
                    isError: true,
                    leadingSystemImage: "envelope"
                )
                AnatomyTextField(
                    text: .constant("No disponible"),
                    label: "Correo electrónico",
                    placeholder: "nombre@empresa.com",
                    leadingSystemImage: "envelope"
                )
                .disabled(true)
            }
        }
    }
}

#Preview("AnatomyFields · Light") {
    AnatomyTextFieldPreview()
        .preferredColorScheme(.light)
}

#Preview("AnatomyFields · Dark") {
    AnatomyTextFieldPreview()
        .preferredColorScheme(.dark)
}
