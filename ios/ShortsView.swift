import SwiftUI

struct ShortsView: View {
    let openConnections: () -> Void
    @State private var platform = ShortsPlatform.youtube

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Minhas Publicações de Shorts")
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(.white)
                    .padding(.top, 18)
                Text("Conteúdos confirmados pelas plataformas")
                    .font(.caption)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .padding(.top, -12)

                Picker("Plataforma", selection: $platform) {
                    ForEach(ShortsPlatform.allCases) { item in
                        Text(item.title).tag(item)
                    }
                }
                .pickerStyle(.segmented)
                .tint(StudioTheme.violet)

                StudioCard {
                    VStack(spacing: 12) {
                        Image(systemName: platform.icon)
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(StudioTheme.lavender)
                        Text("Liga o \(platform.title)")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
                        Text("Autoriza a tua conta para sincronizar vídeos reais do canal.")
                            .font(.subheadline)
                            .foregroundStyle(StudioTheme.secondaryText)
                            .multilineTextAlignment(.center)
                        StudioPrimaryButton(title: "Gerir ligações", action: openConnections)
                            .frame(maxWidth: 150)
                            .padding(.top, 2)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .padding(.top, 2)
            }
            .padding(.horizontal, 18)
        }
        .studioBackground()
    }
}

private enum ShortsPlatform: String, CaseIterable, Identifiable {
    case youtube
    case tikTok

    var id: String { rawValue }
    var title: String { self == .youtube ? "YouTube" : "TikTok" }
    var icon: String { self == .youtube ? "play.rectangle.fill" : "music.note" }
}
