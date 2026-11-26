import SwiftUI

struct HomeDashboardView: View {
    let openConnections: () -> Void
    let openVideos: () -> Void
    let openLives: () -> Void
    let openEditor: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 10) {
                    PlanetEmblem(size: 42)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Short Planet")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
                        Text("STREAM")
                            .font(.caption.weight(.heavy))
                            .tracking(1)
                            .foregroundStyle(StudioTheme.violet)
                    }
                    Spacer()
                    Button(action: openConnections) {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.title2)
                            .foregroundStyle(.white)
                    }
                    .accessibilityLabel("Ligar contas")
                }
                .padding(.top, 8)

                Text("O teu espaço de criação")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.top, 4)

                QuickLinkCard(
                    title: "Vídeos do canal",
                    subtitle: "Consultar vídeos da tua conta YouTube",
                    icon: "play.rectangle.fill",
                    color: .red,
                    action: openVideos
                )

                QuickLinkCard(
                    title: "Lives",
                    subtitle: "O acesso a lives requer uma conta ligada",
                    icon: "dot.radiowaves.left.and.right",
                    color: .red,
                    action: openLives
                )

                HStack {
                    Text("Os teus conteúdos")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Button("Selecionar vídeo", action: openEditor)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(StudioTheme.lavender)
                }
                .padding(.top, 2)

                StudioCard {
                    VStack(spacing: 10) {
                        Image(systemName: "video.slash")
                            .font(.system(size: 30))
                            .foregroundStyle(StudioTheme.lavender)
                            .padding(.top, 5)
                        Text("Ainda sem conteúdos ligados")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                        Text("Liga uma conta YouTube através da integração oficial quando estiver configurada, ou importa um vídeo do iPhone para editar.")
                            .font(.caption)
                            .foregroundStyle(StudioTheme.secondaryText)
                            .multilineTextAlignment(.center)
                        StudioPrimaryButton(title: "Começar", action: openEditor)
                            .frame(maxWidth: 130)
                            .padding(.top, 2)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 20)
        }
        .studioBackground()
    }
}

private struct QuickLinkCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(color)
                    .frame(width: 34)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(StudioTheme.secondaryText)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(StudioTheme.lavender)
            }
            .padding(14)
            .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 17))
            .overlay {
                RoundedRectangle(cornerRadius: 17).stroke(StudioTheme.border, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}
