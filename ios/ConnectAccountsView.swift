import SwiftUI

struct ConnectAccountsView: View {
    let onContinue: () -> Void
    @ObservedObject var youtube: YouTubeConnectionStore
    @ObservedObject var supabaseAuth: SupabaseAuthStore
    @State private var setupAlert: IntegrationPlatform?
    @State private var showingSupabaseAuth = false

    var body: some View {
        VStack(spacing: 0) {
            Text("Conectar")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.top, 14)

            Spacer(minLength: 30)

            VStack(spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 32))
                        .foregroundStyle(Color.red)
                    Text("YouTube")
                        .font(.system(size: 29, weight: .heavy))
                        .foregroundStyle(.white)
                }

                Text("Liga a tua conta do YouTube para acederes aos teus vídeos e transmissões.")
                    .font(.subheadline)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 22)
            }

            Spacer(minLength: 28)

            StudioPrimaryButton(
                title: youtube.isConnected
                    ? "YouTube ligado: \(youtube.channel?.title ?? "Canal")"
                    : "Continuar com Google",
                systemImage: youtube.isConnected ? "checkmark.circle.fill" : "g.circle.fill"
            ) {
                Task { await youtube.connect() }
            }
            .disabled(youtube.isLoading)

            if youtube.isLoading {
                ProgressView("A carregar o canal...")
                    .tint(StudioTheme.lavender)
                    .padding(.top, 10)
            }

            if let error = youtube.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(Color.orange)
                    .multilineTextAlignment(.center)
                    .padding(.top, 10)
            }

            if youtube.isConnected {
                HStack {
                    Text("\(youtube.videos.count) vídeos e transmissões carregados")
                        .font(.caption)
                        .foregroundStyle(StudioTheme.secondaryText)
                    Spacer()
                    Button("Desligar") {
                        Task { await youtube.disconnect() }
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(StudioTheme.lavender)
                }
                .padding(.top, 8)
            }

            Button {
                showingSupabaseAuth = true
            } label: {
                Label(
                    supabaseAuth.user?.email ?? "Entrar ou criar conta Short Planet",
                    systemImage: supabaseAuth.user == nil ? "person.crop.circle.badge.plus" : "person.crop.circle.fill"
                )
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(StudioTheme.lavender)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
            }
            .buttonStyle(.plain)

            HStack(spacing: 12) {
                Rectangle().fill(StudioTheme.border).frame(height: 1)
                Text("ou")
                    .font(.caption)
                    .foregroundStyle(StudioTheme.secondaryText)
                Rectangle().fill(StudioTheme.border).frame(height: 1)
            }
            .padding(.vertical, 22)

            VStack(spacing: 12) {
                Image(systemName: "music.note")
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 48, height: 48)
                    .background(.black, in: RoundedRectangle(cornerRadius: 13))

                Text("Depois de exportar um corte, envia-o pela partilha oficial do iOS e confirma a publicação na app TikTok.")
                    .font(.subheadline)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    setupAlert = .tikTok
                } label: {
                    Label("Partilhar cortes com TikTok", systemImage: "square.and.arrow.up")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(StudioTheme.panel.opacity(0.55), in: RoundedRectangle(cornerRadius: 15))
                        .overlay {
                            RoundedRectangle(cornerRadius: 15)
                                .stroke(
                                    LinearGradient(colors: [StudioTheme.magenta, .blue], startPoint: .leading, endPoint: .trailing),
                                    lineWidth: 1
                                )
                        }
                }
                .buttonStyle(.plain)
            }

            Spacer(minLength: 30)

            Button("Explorar sem ligar contas", action: onContinue)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(StudioTheme.lavender)
                .padding(.bottom, 14)
        }
        .padding(.horizontal, 22)
        .studioBackground()
        .alert(item: $setupAlert) { platform in
            Alert(
                title: Text("\(platform.title) ainda não configurado no iOS"),
                message: Text(platform.setupMessage),
                dismissButton: .default(Text("OK"))
            )
        }
        .sheet(isPresented: $showingSupabaseAuth) {
            SupabaseAuthView(auth: supabaseAuth)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }
}

private struct SupabaseAuthView: View {
    @ObservedObject var auth: SupabaseAuthStore
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var isCreatingAccount = false

    var body: some View {
        VStack(spacing: 16) {
            Text(auth.user == nil ? (isCreatingAccount ? "Criar conta" : "Entrar") : "Conta Short Planet")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)

            if let email = auth.user?.email {
                Text("Sessão iniciada como \(email)")
                    .font(.subheadline)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .multilineTextAlignment(.center)
                Button("Terminar sessão") {
                    Task { await auth.signOut() }
                }
                .disabled(auth.isLoading)
            } else {
                TextField("E-mail", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textContentType(.emailAddress)
                    .padding(12)
                    .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 12))

                SecureField("Palavra-passe", text: $password)
                    .textContentType(isCreatingAccount ? .newPassword : .password)
                    .padding(12)
                    .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 12))

                Button(isCreatingAccount ? "Criar conta" : "Entrar") {
                    Task {
                        if isCreatingAccount {
                            await auth.signUp(email: email, password: password)
                        } else {
                            await auth.signIn(email: email, password: password)
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(StudioTheme.violet)
                .disabled(auth.isLoading || !auth.isConfigured)

                Button(isCreatingAccount ? "Já tenho conta" : "Criar conta") {
                    isCreatingAccount.toggle()
                    auth.errorMessage = nil
                    auth.statusMessage = nil
                }
                .font(.caption)
            }

            if auth.isLoading {
                ProgressView().tint(StudioTheme.lavender)
            }
            if let message = auth.errorMessage ?? auth.configurationMessage {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(Color.orange)
                    .multilineTextAlignment(.center)
            } else if let message = auth.statusMessage {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(StudioTheme.secondaryText)
                    .multilineTextAlignment(.center)
            }

            Button("Fechar") { dismiss() }
                .font(.caption.weight(.medium))
                .foregroundStyle(StudioTheme.lavender)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .studioBackground()
    }
}

enum IntegrationPlatform: String, Identifiable {
    case youtube
    case tikTok

    var id: String { rawValue }

    var title: String {
        switch self {
        case .youtube: "YouTube / Google"
        case .tikTok: "TikTok"
        }
    }

    var setupMessage: String {
        switch self {
        case .youtube:
            "Preenche o cliente OAuth iOS e o URL scheme em ios/IntegrationConfig.xcconfig. A API lista os vídeos e transmissões; para cortar, seleciona o ficheiro original guardado no iPhone."
        case .tikTok:
            "A forma mais simples é exportar o corte e escolher TikTok na folha oficial de partilha do iOS. A app TikTok pede a confirmação final. Publicação automática exige aprovação e um backend seguro."
        }
    }
}
