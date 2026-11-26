import Combine
import GoogleSignIn
import OSLog
import SwiftUI
import UIKit

struct YouTubeChannel: Equatable {
    let channelID: String
    let title: String
    let handle: String
    let thumbnailURL: URL?
    let uploadsPlaylistID: String
}

struct YouTubeVideo: Identifiable, Equatable {
    let videoID: String
    let title: String
    let description: String
    let channelName: String
    let thumbnailURL: URL?
    let publishedAt: String
    let duration: String
    let viewCount: String
    let isLive: Bool

    var id: String { videoID }
    var watchURL: URL { URL(string: "https://www.youtube.com/watch?v=\(videoID)")! }
}

enum YouTubeLibraryFilter: String, CaseIterable, Identifiable {
    case all = "Todos"
    case videos = "Vídeos"
    case lives = "Lives"

    var id: String { rawValue }
}

@MainActor
final class YouTubeConnectionStore: ObservableObject {
    @Published private(set) var isConnected = false
    @Published private(set) var isLoading = false
    @Published private(set) var channel: YouTubeChannel?
    @Published private(set) var videos: [YouTubeVideo] = []
    @Published private(set) var nextPageToken: String?
    @Published var errorMessage: String?

    private var googleUser: GIDGoogleUser?
    private let api = YouTubeDataClient()

    init() {
        guard let clientID = Self.configuredClientID else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        if GIDSignIn.sharedInstance.hasPreviousSignIn() {
            Task { await restorePreviousSignIn() }
        }
    }

    func connect() async {
        guard let clientID = Self.configuredClientID else {
            errorMessage = "Falta o OAuth Client ID iOS. Preenche GOOGLE_IOS_CLIENT_ID e GOOGLE_REVERSED_CLIENT_ID em ios/IntegrationConfig.xcconfig."
            return
        }
        guard let presenter = Self.presentingViewController() else {
            errorMessage = "Não foi possível abrir o ecrã de autorização Google."
            return
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter,
                hint: nil,
                additionalScopes: ["https://www.googleapis.com/auth/youtube.readonly"]
            )
            googleUser = result.user
            let refreshedUser = try await result.user.refreshTokensIfNeeded()
            googleUser = refreshedUser
            try await loadLibrary(accessToken: refreshedUser.accessToken.tokenString)
        } catch {
            errorMessage = Self.userFacingMessage(for: error)
        }
    }

    func refresh() async {
        guard let googleUser else {
            errorMessage = "Liga uma conta Google antes de atualizar o canal."
            return
        }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let user = try await googleUser.refreshTokensIfNeeded()
            self.googleUser = user
            try await loadLibrary(
                accessToken: user.accessToken.tokenString,
                channel: channel,
                pageToken: nil
            )
        } catch {
            errorMessage = Self.userFacingMessage(for: error)
        }
    }

    func loadMore() async {
        guard let googleUser, let channel, let nextPageToken, !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let user = try await googleUser.refreshTokensIfNeeded()
            self.googleUser = user
            let page = try await api.fetchLibrary(
                accessToken: user.accessToken.tokenString,
                channel: channel,
                pageToken: nextPageToken
            )
            videos.append(contentsOf: page.videos.filter { existing in
                !videos.contains(where: { $0.videoID == existing.videoID })
            })
            self.nextPageToken = page.nextPageToken
        } catch {
            errorMessage = Self.userFacingMessage(for: error)
        }
    }

    func disconnect() async {
        do {
            try await GIDSignIn.sharedInstance.disconnect()
        } catch {
            errorMessage = "A conta foi desligada localmente, mas o acesso Google não pôde ser revogado: \(error.localizedDescription)"
        }
        googleUser = nil
        isConnected = false
        channel = nil
        videos = []
        nextPageToken = nil
    }

    private func restorePreviousSignIn() async {
        do {
            let user = try await GIDSignIn.sharedInstance.restorePreviousSignIn()
            googleUser = user
            let refreshedUser = try await user.refreshTokensIfNeeded()
            googleUser = refreshedUser
            try await loadLibrary(accessToken: refreshedUser.accessToken.tokenString)
        } catch {
            errorMessage = Self.userFacingMessage(for: error)
        }
    }

    private func loadLibrary(
        accessToken: String,
        channel existingChannel: YouTubeChannel? = nil,
        pageToken: String? = nil
    ) async throws {
        let page = try await api.fetchLibrary(
            accessToken: accessToken,
            channel: existingChannel,
            pageToken: pageToken
        )
        channel = page.channel
        videos = page.videos
        nextPageToken = page.nextPageToken
        isConnected = true
    }

    private static var configuredClientID: String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String else {
            return nil
        }
        let id = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard id.contains(".apps.googleusercontent.com"), !id.contains("YOUR_") else {
            return nil
        }
        return id
    }

    private static func presentingViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let root = scenes
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        var current = root
        while let presented = current?.presentedViewController {
            current = presented
        }
        return current
    }

    private static func userFacingMessage(for error: Error) -> String {
        return error.localizedDescription
    }
}

private struct YouTubeLibraryPage {
    let channel: YouTubeChannel
    let videos: [YouTubeVideo]
    let nextPageToken: String?
}

private struct YouTubeDataClient {
    private let baseURL = URL(string: "https://www.googleapis.com/youtube/v3/")!
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "com.shortplanetstream.app",
        category: "YouTubeDataClient"
    )

    func fetchLibrary(
        accessToken: String,
        channel existingChannel: YouTubeChannel? = nil,
        pageToken: String? = nil
    ) async throws -> YouTubeLibraryPage {
        let channel: YouTubeChannel
        if let existingChannel {
            channel = existingChannel
        } else {
            channel = try await fetchMyChannel(accessToken: accessToken)
        }
        var playlistQuery = [
            URLQueryItem(name: "part", value: "snippet,contentDetails"),
            URLQueryItem(name: "playlistId", value: channel.uploadsPlaylistID),
            URLQueryItem(name: "maxResults", value: "50")
        ]
        if let pageToken {
            let trimmedPageToken = pageToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedPageToken.isEmpty {
                playlistQuery.append(URLQueryItem(name: "pageToken", value: trimmedPageToken))
            }
        }

        let playlist: PlaylistItemsResponse = try await get(
            "playlistItems",
            query: playlistQuery,
            token: accessToken
        )

        var videoIDs = playlist.items.compactMap { $0.contentDetails?.videoId ?? $0.snippet?.resourceId?.videoId }

        let activeLives: SearchResponse = try await get(
            "search",
            query: [
                URLQueryItem(name: "part", value: "snippet"),
                URLQueryItem(name: "channelId", value: channel.channelID),
                URLQueryItem(name: "type", value: "video"),
                URLQueryItem(name: "eventType", value: "live"),
                URLQueryItem(name: "maxResults", value: "50")
            ],
            token: accessToken
        )
        videoIDs.append(contentsOf: activeLives.items.compactMap { $0.id?.videoId })
        var seenIDs = Set<String>()
        videoIDs = videoIDs.filter { seenIDs.insert($0).inserted }

        var detailsByID: [String: VideosResponse.VideoItem] = [:]
        for start in stride(from: 0, to: videoIDs.count, by: 50) {
            let end = min(start + 50, videoIDs.count)
            let batch = Array(videoIDs[start..<end])
            let details: VideosResponse = try await get(
                "videos",
                query: [
                    URLQueryItem(name: "part", value: "snippet,contentDetails,statistics,liveStreamingDetails"),
                    URLQueryItem(name: "id", value: batch.joined(separator: ","))
                ],
                token: accessToken
            )

            for item in details.items {
                detailsByID[item.id] = item
            }
        }

        let videos = videoIDs.compactMap { id -> YouTubeVideo? in
            guard let item = detailsByID[id], let snippet = item.snippet else { return nil }
            let liveState = snippet.liveBroadcastContent?.lowercased()
            let isLive = liveState == "live" || liveState == "upcoming" || item.liveStreamingDetails != nil
            let thumb = snippet.thumbnails?.maxres?.url
                ?? snippet.thumbnails?.standard?.url
                ?? snippet.thumbnails?.high?.url
                ?? snippet.thumbnails?.medium?.url
                ?? snippet.thumbnails?.default?.url
            return YouTubeVideo(
                videoID: item.id,
                title: snippet.title ?? "Vídeo sem título",
                description: snippet.description ?? "",
                channelName: snippet.channelTitle ?? channel.title,
                thumbnailURL: thumb.flatMap(URL.init(string:)),
                publishedAt: snippet.publishedAt ?? "",
                duration: liveState == "live" ? "AO VIVO" : Self.formatDuration(item.contentDetails?.duration),
                viewCount: item.statistics?.viewCount ?? "0",
                isLive: isLive
            )
        }

        return YouTubeLibraryPage(
            channel: channel,
            videos: videos,
            nextPageToken: playlist.nextPageToken
        )
    }

    private func fetchMyChannel(accessToken: String) async throws -> YouTubeChannel {
        let response: ChannelsResponse = try await get(
            "channels",
            query: [
                URLQueryItem(name: "part", value: "snippet,contentDetails"),
                URLQueryItem(name: "mine", value: "true")
            ],
            token: accessToken
        )
        guard let item = response.items.first,
              let uploads = item.contentDetails?.relatedPlaylists?.uploads else {
            throw YouTubeIntegrationError.noChannel
        }
        let thumbnail = item.snippet?.thumbnails?.high?.url
            ?? item.snippet?.thumbnails?.medium?.url
            ?? item.snippet?.thumbnails?.default?.url
        return YouTubeChannel(
            channelID: item.id,
            title: item.snippet?.title ?? "Canal YouTube",
            handle: item.snippet?.customUrl ?? "",
            thumbnailURL: thumbnail.flatMap(URL.init(string:)),
            uploadsPlaylistID: uploads
        )
    }

    private func get<Response: Decodable>(
        _ path: String,
        query: [URLQueryItem],
        token: String
    ) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil }
        guard let url = components.url else { throw YouTubeIntegrationError.invalidRequest }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let result: (Data, URLResponse)
        do {
            result = try await URLSession.shared.data(for: request)
        } catch {
            logger.error(
                "YouTube request failed before an HTTP response: endpoint=\(url.path, privacy: .public), error=\(error.localizedDescription, privacy: .public)"
            )
            throw error
        }

        let (data, response) = result
        guard let http = response as? HTTPURLResponse else {
            logger.error(
                "YouTube request returned a non-HTTP response: endpoint=\(url.path, privacy: .public)"
            )
            throw YouTubeIntegrationError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            let responseBody = String(data: data, encoding: .utf8) ?? "<non-UTF-8 response>"
            logger.error(
                "YouTube API error: endpoint=\(url.path, privacy: .public), HTTP \(http.statusCode, privacy: .public), body=\(responseBody, privacy: .public)"
            )
            let apiMessage = (try? JSONDecoder().decode(GoogleAPIErrorResponse.self, from: data).error.message)
            if http.statusCode == 401 {
                throw YouTubeIntegrationError.unauthorized
            }
            throw YouTubeIntegrationError.api(status: http.statusCode, message: apiMessage)
        }
        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            let responseBody = String(data: data, encoding: .utf8) ?? "<non-UTF-8 response>"
            logger.error(
                "YouTube API response decoding failed: endpoint=\(url.path, privacy: .public), HTTP \(http.statusCode, privacy: .public), body=\(responseBody, privacy: .public)"
            )
            throw error
        }
    }

    private static func formatDuration(_ iso: String?) -> String {
        guard let iso else { return "—" }
        let pattern = #"P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?"#
        guard let match = iso.range(of: pattern, options: .regularExpression) else { return "—" }
        let parts = String(iso[match])
        let captures = try? NSRegularExpression(pattern: pattern)
            .firstMatch(in: parts, range: NSRange(parts.startIndex..., in: parts))
        func number(_ index: Int) -> Int {
            guard let captures, let range = Range(captures.range(at: index), in: parts) else { return 0 }
            return Int(parts[range]) ?? 0
        }
        let total = number(1) * 86_400 + number(2) * 3_600 + number(3) * 60 + number(4)
        let hours = total / 3_600
        let minutes = (total % 3_600) / 60
        let seconds = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, seconds)
            : String(format: "%02d:%02d", minutes, seconds)
    }
}

private enum YouTubeIntegrationError: LocalizedError {
    case noChannel
    case invalidRequest
    case invalidResponse
    case unauthorized
    case api(status: Int, message: String?)

    var errorDescription: String? {
        switch self {
        case .noChannel:
            "A conta Google não tem um canal YouTube disponível."
        case .invalidRequest:
            "Não foi possível preparar o pedido à API do YouTube."
        case .invalidResponse:
            "A API do YouTube devolveu uma resposta inválida."
        case .unauthorized:
            "A autorização expirou. Desliga e liga novamente a conta Google."
        case let .api(status, message):
            message ?? "Erro da API YouTube (HTTP \(status)). Confirma se a YouTube Data API v3 está ativa e se os scopes foram aprovados."
        }
    }
}

private struct GoogleAPIErrorResponse: Decodable {
    struct Detail: Decodable {
        let message: String?
    }
    let error: Detail
}

private struct ChannelsResponse: Decodable {
    let items: [ChannelItem]
}

private struct ChannelItem: Decodable {
    struct Snippet: Decodable {
        let title: String?
        let customUrl: String?
        let thumbnails: ThumbnailSet?
    }
    struct ContentDetails: Decodable {
        struct RelatedPlaylists: Decodable {
            let uploads: String?
        }
        let relatedPlaylists: RelatedPlaylists?
    }
    let snippet: Snippet?
    let contentDetails: ContentDetails?
    let id: String
}

private struct PlaylistItemsResponse: Decodable {
    struct PlaylistItem: Decodable {
        struct Snippet: Decodable {
            struct ResourceId: Decodable {
                let videoId: String?
            }
            let resourceId: ResourceId?
        }
        struct ContentDetails: Decodable {
            let videoId: String?
        }
        let snippet: Snippet?
        let contentDetails: ContentDetails?
    }
    let nextPageToken: String?
    let items: [PlaylistItem]
}

private struct SearchResponse: Decodable {
    struct SearchItem: Decodable {
        struct ResourceId: Decodable {
            let videoId: String?
        }
        let id: ResourceId?
    }
    let items: [SearchItem]
}

private struct VideosResponse: Decodable {
    struct VideoItem: Decodable {
        struct Snippet: Decodable {
            let title: String?
            let description: String?
            let channelTitle: String?
            let publishedAt: String?
            let liveBroadcastContent: String?
            let thumbnails: ThumbnailSet?
        }
        struct ContentDetails: Decodable {
            let duration: String?
        }
        struct Statistics: Decodable {
            let viewCount: String?
        }
        let id: String
        let snippet: Snippet?
        let contentDetails: ContentDetails?
        let statistics: Statistics?
        let liveStreamingDetails: [String: String]?
    }
    let items: [VideoItem]
}

private struct ThumbnailSet: Decodable {
    struct Thumbnail: Decodable {
        let url: String?
    }
    let `default`: Thumbnail?
    let medium: Thumbnail?
    let high: Thumbnail?
    let standard: Thumbnail?
    let maxres: Thumbnail?
}
