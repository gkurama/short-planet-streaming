import Combine
import Foundation
import Supabase

@MainActor
final class SupabaseAuthStore: ObservableObject {
    @Published private(set) var user: User?
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published private(set) var configurationMessage: String?

    private let client: SupabaseClient?

    init() {
        do {
            client = try Self.makeClient()
        } catch {
            client = nil
            configurationMessage = error.localizedDescription
        }
    }

    var isConfigured: Bool { client != nil }

    func accessTokenForBackend() async throws -> String {
        guard let client else {
            throw configurationMessage.map { NSError(domain: "Supabase", code: 1, userInfo: [NSLocalizedDescriptionKey: $0]) }
                ?? NSError(domain: "Supabase", code: 1, userInfo: [NSLocalizedDescriptionKey: "A configuração do Supabase está indisponível."])
        }
        let session = try await client.auth.session
        return session.accessToken
    }

    func restoreSession() async {
        guard let client, client.auth.currentSession != nil else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let session = try await client.auth.session
            user = session.user
            try await persistProfile(for: session.user, using: client)
        } catch {
            errorMessage = "A sessão foi recuperada, mas não foi possível validar ou sincronizar o perfil: \(error.localizedDescription)"
        }
    }

    func signIn(email: String, password: String) async {
        guard let client else {
            errorMessage = configurationMessage ?? "A configuração do Supabase está indisponível."
            return
        }
        guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.isEmpty else {
            errorMessage = "Preenche o e-mail e a palavra-passe."
            return
        }

        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let session = try await client.auth.signIn(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            user = session.user
            try await persistProfile(for: session.user, using: client)
            statusMessage = "Sessão iniciada e perfil sincronizado."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func signUp(email: String, password: String) async {
        guard let client else {
            errorMessage = configurationMessage ?? "A configuração do Supabase está indisponível."
            return
        }
        guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.isEmpty else {
            errorMessage = "Preenche o e-mail e a palavra-passe."
            return
        }

        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let response = try await client.auth.signUp(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            guard let session = response.session else {
                statusMessage = "Confirma o e-mail enviado antes de iniciar sessão."
                return
            }
            user = session.user
            try await persistProfile(for: session.user, using: client)
            statusMessage = "Conta criada e perfil sincronizado."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func signOut() async {
        guard let client else { return }
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            try await client.auth.signOut()
            user = nil
            statusMessage = "Sessão terminada."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func persistProfile(for user: User, using client: SupabaseClient) async throws {
        guard let email = user.email else {
            throw SupabaseIntegrationError.profileEmailUnavailable
        }

        let userID = user.id.uuidString
        let _: [ProfileSchemaRow] = try await client
            .from("usuarios")
            .select("id,email")
            .eq("id", value: userID)
            .limit(1)
            .execute()
            .value

        try await client
            .from("usuarios")
            .upsert(UserProfileRecord(id: userID, email: email), onConflict: "id")
            .execute()
    }

    private static func makeClient() throws -> SupabaseClient {
        guard let rawURL = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              let url = URL(string: rawURL),
              url.scheme == "https",
              url.host != nil,
              !rawURL.contains("$(") else {
            throw SupabaseIntegrationError.missingProjectURL
        }
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String,
              !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !key.contains("$(") else {
            throw SupabaseIntegrationError.missingPublishableKey
        }
        guard !isAdministrativeKey(key) else {
            throw SupabaseIntegrationError.administrativeKeyRejected
        }

        return SupabaseClient(supabaseURL: url, supabaseKey: key)
    }

    private static func isAdministrativeKey(_ key: String) -> Bool {
        if key.hasPrefix("sb_secret_") { return true }

        let parts = key.split(separator: ".")
        guard parts.count == 3 else { return false }
        let encodedPayload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let paddedPayload = encodedPayload.padding(
            toLength: ((encodedPayload.count + 3) / 4) * 4,
            withPad: "=",
            startingAt: 0
        )
        guard let data = Data(base64Encoded: paddedPayload),
              let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return false
        }
        return (payload["role"] as? String)?.lowercased() == "service_role"
    }
}

private struct ProfileSchemaRow: Decodable {
    let id: String
    let email: String?
}

private struct UserProfileRecord: Encodable {
    let id: String
    let email: String
}

private enum SupabaseIntegrationError: LocalizedError {
    case missingProjectURL
    case missingPublishableKey
    case administrativeKeyRejected
    case profileEmailUnavailable

    var errorDescription: String? {
        switch self {
        case .missingProjectURL:
            "Configura SUPABASE_URL em ios/IntegrationConfig.local.xcconfig."
        case .missingPublishableKey:
            "Configura SUPABASE_PUBLISHABLE_KEY em ios/IntegrationConfig.local.xcconfig."
        case .administrativeKeyRejected:
            "A chave administrativa do Supabase não pode ser usada no aplicativo iOS."
        case .profileEmailUnavailable:
            "O Supabase Auth não forneceu um e-mail para persistir o perfil."
        }
    }
}
