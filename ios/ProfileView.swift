import SwiftUI

struct ProfileView: View {
    let openConnections: () -> Void
    @ObservedObject var supabaseAuth: SupabaseAuthStore
    @State private var destination: ProfileDestination?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                PlanetEmblem(size: 72)
                    .padding(.top, 28)
                Text("Short Planet Stream")
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(.white)
                Text("Contas externas não ligadas")
                    .font(.subheadline)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .padding(.bottom, 2)

                VStack(spacing: 0) {
                    ProfileRow(
                        icon: "person.2.fill",
                        title: "Contas conectadas",
                        subtitle: "YouTube · TikTok",
                        action: openConnections
                    )
                    Divider().overlay(StudioTheme.border)
                    ProfileRow(
                        icon: "gearshape.fill",
                        title: "Configurações",
                        subtitle: "Preferências da aplicação",
                        action: { destination = .settings }
                    )
                    Divider().overlay(StudioTheme.border)
                    ProfileRow(
                        icon: "info.circle.fill",
                        title: "Sobre o app",
                        subtitle: "Versão 0.5.0",
                        action: { destination = .about }
                    )
                }
                .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 18))
                .overlay {
                    RoundedRectangle(cornerRadius: 18).stroke(StudioTheme.border, lineWidth: 1)
                }

                Text(supabaseAuth.user?.email.map { "Sessão Short Planet iniciada como \($0)." }
                     ?? "A explorar sem sessão Short Planet. Inicia sessão em Contas conectadas para guardar dados pessoais.")
                    .font(.caption)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
            }
            .padding(.horizontal, 18)
        }
        .studioBackground()
        .sheet(item: $destination) { destination in
            ProfileInfoSheet(destination: destination)
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
    }
}

private struct ProfileRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: icon)
                    .font(.body.weight(.bold))
                    .foregroundStyle(StudioTheme.lavender)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(StudioTheme.secondaryText)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(StudioTheme.secondaryText)
            }
            .padding(15)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private enum ProfileDestination: String, Identifiable {
    case settings
    case about

    var id: String { rawValue }
}

private struct ProfileInfoSheet: View {
    let destination: ProfileDestination

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: destination == .settings ? "gearshape.fill" : "info.circle.fill")
                .font(.system(size: 30))
                .foregroundStyle(StudioTheme.lavender)
            Text(destination == .settings ? "Configurações" : "Sobre o app")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
            Text(destination == .settings
                 ? "As preferências e a autenticação serão ligadas quando os serviços iOS correspondentes estiverem configurados."
                 : "Short Planet Stream · Versão 0.5.0\nAplicação de criação de vídeos curtos.")
                .font(.subheadline)
                .foregroundStyle(StudioTheme.secondaryText)
                .multilineTextAlignment(.center)
            if destination == .settings {
                Label("Tema escuro", systemImage: "moon.fill")
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.top, 4)
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(StudioTheme.background.ignoresSafeArea())
    }
}
