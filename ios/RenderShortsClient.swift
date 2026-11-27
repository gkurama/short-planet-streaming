import Foundation

struct RenderPublishResult: Decodable {
    let videoID: String
    let watchURL: URL
    let privacyStatus: String
    let durationSeconds: Double

    enum CodingKeys: String, CodingKey {
        case videoID = "video_id"
        case watchURL = "watch_url"
        case privacyStatus = "privacy_status"
        case durationSeconds = "duration_seconds"
    }
}

private struct RenderPublishMetadata: Encodable {
    let startSeconds: Double
    let endSeconds: Double
    let title: String
    let description: String
    let privacyStatus: String
    let madeForKids: Bool

    enum CodingKeys: String, CodingKey {
        case startSeconds = "start_seconds"
        case endSeconds = "end_seconds"
        case title
        case description
        case privacyStatus = "privacy_status"
        case madeForKids = "made_for_kids"
    }
}

private struct RenderJobResponse: Decodable {
    let jobID: String

    enum CodingKeys: String, CodingKey {
        case jobID = "job_id"
    }
}

enum RenderShortsClient {
    static func publish(
        sourceURL: URL,
        startSeconds: Double,
        endSeconds: Double,
        title: String,
        description: String,
        privacyStatus: String,
        madeForKids: Bool,
        supabaseAccessToken: String,
        youtubeAccessToken: String
    ) async throws -> RenderPublishResult {
        guard let baseValue = Bundle.main.object(forInfoDictionaryKey: "RenderAPIBaseURL") as? String,
              let baseURL = URL(string: baseValue.trimmingCharacters(in: .whitespacesAndNewlines)),
              baseURL.scheme == "https",
              let host = baseURL.host,
              !host.isEmpty,
              !baseValue.contains("$(") else {
            throw RenderShortsError.notConfigured
        }
        guard FileManager.default.fileExists(atPath: sourceURL.path) else {
            throw RenderShortsError.sourceUnavailable
        }
        guard endSeconds > startSeconds, endSeconds - startSeconds <= 180 else {
            throw RenderShortsError.invalidRange
        }

        let ext = sourceURL.pathExtension.lowercased()
        let mimeType = ext == "mov" ? "video/quicktime" : (ext == "m4v" ? "video/x-m4v" : "video/mp4")
        let metadata = RenderPublishMetadata(
            startSeconds: startSeconds,
            endSeconds: endSeconds,
            title: title,
            description: description,
            privacyStatus: privacyStatus,
            madeForKids: madeForKids
        )
        var jobsEndpoint = baseURL
        jobsEndpoint.appendPathComponent("v1")
        jobsEndpoint.appendPathComponent("shorts")
        jobsEndpoint.appendPathComponent("jobs")
        var createRequest = URLRequest(url: jobsEndpoint)
        createRequest.httpMethod = "POST"
        createRequest.timeoutInterval = 60
        createRequest.setValue("Bearer \(supabaseAccessToken)", forHTTPHeaderField: "Authorization")
        createRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        createRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        createRequest.httpBody = try JSONEncoder().encode(metadata)

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5_400
        configuration.timeoutIntervalForResource = 5_400
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }

        let (jobData, jobResponse) = try await session.data(for: createRequest)
        guard let jobHTTPResponse = jobResponse as? HTTPURLResponse else {
            throw RenderShortsError.invalidResponse
        }
        guard (200..<300).contains(jobHTTPResponse.statusCode) else {
            let detail = (try? JSONDecoder().decode(RenderErrorResponse.self, from: jobData).detail)
            throw RenderShortsError.server(detail ?? "O Render recusou preparar o processamento (HTTP \(jobHTTPResponse.statusCode)).")
        }

        let jobID: String
        do {
            jobID = try JSONDecoder().decode(RenderJobResponse.self, from: jobData).jobID
        } catch {
            throw RenderShortsError.invalidResponse
        }

        var uploadEndpoint = baseURL
        uploadEndpoint.appendPathComponent("v1")
        uploadEndpoint.appendPathComponent("shorts")
        uploadEndpoint.appendPathComponent("jobs")
        uploadEndpoint.appendPathComponent(jobID)
        uploadEndpoint.appendPathComponent("source")
        var uploadRequest = URLRequest(url: uploadEndpoint)
        uploadRequest.httpMethod = "PUT"
        uploadRequest.timeoutInterval = 5_400
        uploadRequest.setValue("Bearer \(supabaseAccessToken)", forHTTPHeaderField: "Authorization")
        uploadRequest.setValue("Bearer \(youtubeAccessToken)", forHTTPHeaderField: "X-YouTube-Authorization")
        uploadRequest.setValue(mimeType, forHTTPHeaderField: "Content-Type")
        uploadRequest.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await session.upload(for: uploadRequest, fromFile: sourceURL)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw RenderShortsError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let detail = (try? JSONDecoder().decode(RenderErrorResponse.self, from: data).detail)
            throw RenderShortsError.server(detail ?? "O Render recusou o processamento (HTTP \(httpResponse.statusCode)).")
        }
        do {
            return try JSONDecoder().decode(RenderPublishResult.self, from: data)
        } catch {
            throw RenderShortsError.invalidResponse
        }
    }
}

private struct RenderErrorResponse: Decodable {
    let detail: String
}

enum RenderShortsError: LocalizedError {
    case notConfigured
    case sourceUnavailable
    case invalidRange
    case invalidResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            "O endereço do backend Render ainda não está configurado nesta versão da app."
        case .sourceUnavailable:
            "Não foi possível aceder ao vídeo selecionado no aparelho."
        case .invalidRange:
            "O trecho precisa ter até 3 minutos."
        case .invalidResponse:
            "O backend devolveu uma resposta inválida."
        case let .server(message):
            message
        }
    }
}
