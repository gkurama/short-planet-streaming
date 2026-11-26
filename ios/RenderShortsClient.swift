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

        let boundary = "ShortPlanet-\(UUID().uuidString)"
        let multipartURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("shortplanet-upload-\(UUID().uuidString).multipart")
        FileManager.default.createFile(atPath: multipartURL.path, contents: nil)
        defer { try? FileManager.default.removeItem(at: multipartURL) }

        let output = try FileHandle(forWritingTo: multipartURL)
        defer { try? output.close() }

        func writeField(_ name: String, _ value: String) throws {
            let header = "--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n"
            try output.write(contentsOf: Data(header.utf8))
            try output.write(contentsOf: Data(value.utf8))
            try output.write(contentsOf: Data("\r\n".utf8))
        }

        try writeField("start_seconds", String(startSeconds))
        try writeField("end_seconds", String(endSeconds))
        try writeField("title", title)
        try writeField("description", description)
        try writeField("privacy_status", privacyStatus)
        try writeField("made_for_kids", madeForKids ? "true" : "false")

        let ext = sourceURL.pathExtension.lowercased()
        let mimeType = ext == "mov" ? "video/quicktime" : (ext == "m4v" ? "video/x-m4v" : "video/mp4")
        let safeExtension = ["mp4", "mov", "m4v", "mkv", "webm", "avi"].contains(ext) ? ext : "mp4"
        let fileHeader = "--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"source.\(safeExtension)\"\r\nContent-Type: \(mimeType)\r\n\r\n"
        try output.write(contentsOf: Data(fileHeader.utf8))

        let input = try FileHandle(forReadingFrom: sourceURL)
        defer { try? input.close() }
        while let chunk = try input.read(upToCount: 1024 * 1024), !chunk.isEmpty {
            try output.write(contentsOf: chunk)
        }
        try output.write(contentsOf: Data("\r\n--\(boundary)--\r\n".utf8))
        try output.close()

        let endpoint = baseURL.appendingPathComponent("v1/shorts/publish")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 5_400
        request.setValue("Bearer \(supabaseAccessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("Bearer \(youtubeAccessToken)", forHTTPHeaderField: "X-YouTube-Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5_400
        configuration.timeoutIntervalForResource = 5_400
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.upload(for: request, fromFile: multipartURL)
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
