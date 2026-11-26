import SwiftUI

enum StudioTheme {
    static let background = Color(red: 0.025, green: 0.018, blue: 0.075)
    static let panel = Color(red: 0.075, green: 0.055, blue: 0.12)
    static let panelRaised = Color(red: 0.105, green: 0.075, blue: 0.16)
    static let violet = Color(red: 0.49, green: 0.14, blue: 0.98)
    static let magenta = Color(red: 0.69, green: 0.13, blue: 0.96)
    static let lavender = Color(red: 0.76, green: 0.55, blue: 1.0)
    static let secondaryText = Color(red: 0.66, green: 0.63, blue: 0.72)
    static let border = Color.white.opacity(0.08)
}

struct StudioBackground: ViewModifier {
    func body(content: Content) -> some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.11, green: 0.025, blue: 0.22),
                    StudioTheme.background,
                    Color(red: 0.015, green: 0.025, blue: 0.12)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            content
        }
    }
}

extension View {
    func studioBackground() -> some View {
        modifier(StudioBackground())
    }
}

struct StudioCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 18))
            .overlay {
                RoundedRectangle(cornerRadius: 18)
                    .stroke(StudioTheme.border, lineWidth: 1)
            }
    }
}

struct StudioPrimaryButton: View {
    let title: String
    var systemImage: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                if let systemImage {
                    Image(systemName: systemImage)
                }
                Text(title)
                    .fontWeight(.semibold)
            }
            .font(.subheadline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(
                LinearGradient(
                    colors: [StudioTheme.violet, StudioTheme.magenta],
                    startPoint: .leading,
                    endPoint: .trailing
                ),
                in: RoundedRectangle(cornerRadius: 15)
            )
        }
        .buttonStyle(.plain)
    }
}

struct PlanetEmblem: View {
    var size: CGFloat = 42

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [StudioTheme.magenta, StudioTheme.violet, Color.blue],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: size * 0.78, height: size * 0.78)
            Image(systemName: "play.fill")
                .font(.system(size: size * 0.32, weight: .bold))
                .foregroundStyle(.white)
                .offset(x: 1)
            Ellipse()
                .stroke(
                    LinearGradient(
                        colors: [.cyan, StudioTheme.lavender],
                        startPoint: .leading,
                        endPoint: .trailing
                    ),
                    lineWidth: max(2, size * 0.07)
                )
                .frame(width: size, height: size * 0.38)
                .rotationEffect(.degrees(-24))
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Short Planet Stream")
    }
}
