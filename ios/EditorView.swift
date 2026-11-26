import AVFoundation
import AVKit
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct EditorView: View {
    let templateName: String?
    @ObservedObject var youtube: YouTubeConnectionStore
    @ObservedObject var supabaseAuth: SupabaseAuthStore
    let openConnections: () -> Void

    @State private var selectedPhoto: PhotosPickerItem?
    @State private var sourceURL: URL?
    @State private var sourceDuration: Double = 0
    @State private var player: AVPlayer?
    @State private var videoComposition: AVMutableVideoComposition?
    @State private var trimStart: Double = 0
    @State private var trimEnd: Double = 0
    @State private var exportTitle = ""
    @State private var exportedURL: URL?
    @State private var isImporting = false
    @State private var isExporting = false
    @State private var errorMessage: String?
    @State private var publishErrorMessage: String?
    @State private var publishStatusMessage: String?
    @State private var isPublishing = false
    @State private var hasPublishingRights = false
    @State private var confirmsCommunityGuidelines = false
    @State private var privacyStatus = "unlisted"
    @State private var madeForKids: Bool?
    @State private var publishDescription = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Editor")
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(.white)
                    .padding(.top, 12)

                StudioCard {
                    VStack(spacing: 12) {
                        Image(systemName: sourceURL == nil ? "film" : "checkmark.rectangle.fill")
                            .font(.system(size: 30))
                            .foregroundStyle(StudioTheme.lavender)

                        Text(sourceURL == nil ? "Importa o original do iPhone" : (sourceURL?.lastPathComponent ?? "Vídeo selecionado"))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .lineLimit(2)

                        Text(sourceURL == nil
                         ? "Seleciona o ficheiro correspondente ao item do YouTube. O editor prepara a prévia e a timeline sem alterar o original."
                         : "Prévia vertical 9:16. Ajusta os marcadores para escolher o trecho; o original não é alterado.")
                            .font(.caption)
                            .foregroundStyle(StudioTheme.secondaryText)
                            .multilineTextAlignment(.center)

                        PhotosPicker(selection: $selectedPhoto, matching: .videos) {
                            Label(
                                sourceURL == nil ? "Importar original" : "Escolher outro original",
                                systemImage: "plus"
                            )
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 11)
                            .background(StudioTheme.violet, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(isImporting || isExporting)

                        if isImporting {
                            ProgressView("A importar vídeo...")
                                .tint(StudioTheme.lavender)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }

                if sourceURL != nil, sourceDuration > 1 {
                    StudioCard {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Prévia do Short")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(.white)

                            if let player {
                                VideoPlayer(player: player)
                                    .frame(height: 300)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                    .overlay(alignment: .topTrailing) {
                                        Text("9:16")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 9)
                                            .padding(.vertical, 5)
                                            .background(.black.opacity(0.7), in: Capsule())
                                            .padding(10)
                                    }
                            }

                            HStack {
                                Text("INÍCIO")
                                Spacer()
                                Text(Self.timeLabel(trimStart))
                                    .monospacedDigit()
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(StudioTheme.lavender)

                            TrimRangeTimeline(
                                start: $trimStart,
                                end: $trimEnd,
                                duration: sourceDuration
                            )

                            HStack {
                                Text("FIM")
                                Spacer()
                                Text(Self.timeLabel(trimEnd))
                                    .monospacedDigit()
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(StudioTheme.lavender)

                            Text("Duração do corte: \(Self.timeLabel(max(0, trimEnd - trimStart)))")
                                .font(.caption)
                                .foregroundStyle(StudioTheme.secondaryText)

                            TextField("Título do corte", text: $exportTitle)
                                .textFieldStyle(.roundedBorder)

                            StudioPrimaryButton(
                                title: isExporting ? "A criar Short..." : "Criar Short 9:16",
                                systemImage: "scissors"
                            ) {
                                Task { await exportTrimmedVideo() }
                            }
                            .disabled(isExporting || trimEnd <= trimStart)

                            if let exportedURL {
                                ShareLink(item: exportedURL) {
                                    Label("Partilhar Short", systemImage: "square.and.arrow.up")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(StudioTheme.panelRaised, in: RoundedRectangle(cornerRadius: 14))
                                }
                                .accessibilityHint("Abre a folha oficial de partilha do iOS para escolheres onde enviar o Short.")
                                Text("Escolhe a aplicação de destino na folha de partilha.")
                                    .font(.caption)
                                    .foregroundStyle(StudioTheme.secondaryText)
                            }

                            Divider().overlay(StudioTheme.border)

                            Text("Processar e publicar no YouTube")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(.white)

                            Text("O Render recebe o arquivo escolhido no aparelho, cria o trecho vertical e o envia ao canal autorizado. O arquivo temporário é apagado ao terminar o pedido.")
                                .font(.caption)
                                .foregroundStyle(StudioTheme.secondaryText)

                            TextField("Descrição do Short (opcional)", text: $publishDescription, axis: .vertical)
                                .textFieldStyle(.roundedBorder)
                                .lineLimit(2...5)

                            Picker("Visibilidade no YouTube", selection: $privacyStatus) {
                                Text("Não listado").tag("unlisted")
                                Text("Privado").tag("private")
                                Text("Público").tag("public")
                            }
                            .tint(StudioTheme.lavender)

                            Picker("Conteúdo feito para crianças?", selection: $madeForKids) {
                                Text("Escolhe uma opção").tag(nil as Bool?)
                                Text("Sim").tag(true as Bool?)
                                Text("Não").tag(false as Bool?)
                            }
                            .tint(StudioTheme.lavender)

                            Toggle("Confirmo que tenho direito de editar e publicar este arquivo", isOn: $hasPublishingRights)
                                .font(.caption)
                                .tint(StudioTheme.violet)

                            Toggle("Confirmo que este envio respeita as Diretrizes da Comunidade do YouTube", isOn: $confirmsCommunityGuidelines)
                                .font(.caption)
                                .tint(StudioTheme.violet)

                            if supabaseAuth.user == nil || !youtube.isConnected {
                                Button("Conectar conta do app e YouTube", action: openConnections)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(StudioTheme.lavender)
                            }

                            StudioPrimaryButton(
                                title: isPublishing ? "A processar e enviar..." : "Publicar Short no YouTube",
                                systemImage: "arrow.up.circle.fill"
                            ) {
                                Task { await publishToYouTube() }
                            }
                            .disabled(
                                isPublishing || isExporting || !hasPublishingRights || !confirmsCommunityGuidelines ||
                                    madeForKids == nil || titleIsInvalid || trimEnd - trimStart > 180 ||
                                    supabaseAuth.user == nil || !youtube.isConnected
                            )

                            if isPublishing {
                                ProgressView("O vídeo está a ser processado no Render...")
                                    .tint(StudioTheme.lavender)
                            }
                            if let publishStatusMessage {
                                Text(publishStatusMessage)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(StudioTheme.lavender)
                            }
                            if let publishErrorMessage {
                                Text(publishErrorMessage)
                                    .font(.caption)
                                    .foregroundStyle(.orange)
                            }
                        }
                    }
                }

                if let templateName {
                    StudioCard {
                        Label("Template aplicado: \(templateName)", systemImage: "sparkles")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(StudioTheme.lavender)
                    }
                }

                if isExporting {
                    ProgressView("A criar o MP4...")
                        .tint(StudioTheme.lavender)
                        .frame(maxWidth: .infinity)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(Color.orange)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 24)
        }
        .studioBackground()
        .onChange(of: selectedPhoto) { _, item in
            guard let item else { return }
            Task { await importVideo(item) }
        }
        .onChange(of: trimStart) { _, start in
            if start >= trimEnd, sourceDuration > 1 {
                trimEnd = min(sourceDuration, start + 1)
            }
        }
        .onChange(of: trimEnd) { _, end in
            if end <= trimStart, sourceDuration > 1 {
                trimStart = max(0, end - 1)
            }
        }
        .onDisappear {
            player?.pause()
        }
    }

    @MainActor
    private func importVideo(_ item: PhotosPickerItem) async {
        isImporting = true
        errorMessage = nil
        exportedURL = nil
        defer {
            isImporting = false
            selectedPhoto = nil
        }

        do {
            guard let movie = try await item.loadTransferable(type: ImportedMovie.self) else {
                throw EditorError.cannotReadVideo
            }
            let asset = AVURLAsset(url: movie.url)
            let seconds = try await asset.load(.duration).seconds
            guard seconds.isFinite, seconds > 1 else {
                throw EditorError.invalidDuration
            }
            let composition = try await Self.makeVerticalVideoComposition(for: asset)
            let playerItem = AVPlayerItem(asset: asset)
            playerItem.videoComposition = composition
            sourceURL = movie.url
            sourceDuration = seconds
            videoComposition = composition
            player = AVPlayer(playerItem: playerItem)
            trimStart = 0
            trimEnd = min(seconds, 60)
            if exportTitle.isEmpty {
                exportTitle = movie.url.deletingPathExtension().lastPathComponent
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func exportTrimmedVideo() async {
        guard let sourceURL, sourceDuration > 1 else {
            errorMessage = EditorError.cannotReadVideo.localizedDescription
            return
        }
        let start = min(max(trimStart, 0), sourceDuration - 1)
        let end = min(max(trimEnd, start + 1), sourceDuration)
        guard end > start else {
            errorMessage = EditorError.invalidRange.localizedDescription
            return
        }

        isExporting = true
        errorMessage = nil
        exportedURL = nil
        defer { isExporting = false }

        do {
            let asset = AVURLAsset(url: sourceURL)
            guard let videoComposition else {
                throw EditorError.cannotPrepareVideo
            }
            guard let session = AVAssetExportSession(
                asset: asset,
                presetName: AVAssetExportPresetHighestQuality
            ) else {
                throw EditorError.exportUnavailable
            }

            let destination = FileManager.default.temporaryDirectory
                .appendingPathComponent("short-\(UUID().uuidString)")
                .appendingPathExtension("mp4")
            session.outputURL = destination
            session.outputFileType = .mp4
            session.shouldOptimizeForNetworkUse = true
            session.videoComposition = videoComposition
            session.timeRange = CMTimeRange(
                start: CMTime(seconds: start, preferredTimescale: 600),
                duration: CMTime(seconds: end - start, preferredTimescale: 600)
            )

            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                session.exportAsynchronously {
                    switch session.status {
                    case .completed:
                        continuation.resume()
                    case .failed:
                        continuation.resume(throwing: session.error ?? EditorError.exportFailed)
                    case .cancelled:
                        continuation.resume(throwing: EditorError.exportCancelled)
                    default:
                        continuation.resume(throwing: EditorError.exportFailed)
                    }
                }
            }
            exportedURL = destination
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private var titleIsInvalid: Bool {
        let title = exportTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        return title.isEmpty || title.count > 100
    }

    @MainActor
    private func publishToYouTube() async {
        guard let sourceURL, trimEnd > trimStart else {
            publishErrorMessage = "Importa um arquivo e marca o trecho que queres publicar."
            return
        }
        guard hasPublishingRights, confirmsCommunityGuidelines, let madeForKids else { return }
        guard trimEnd - trimStart <= 180 else {
            publishErrorMessage = "Um Short pode ter até 3 minutos. Ajusta o fim do trecho."
            return
        }

        isPublishing = true
        publishErrorMessage = nil
        publishStatusMessage = nil
        defer { isPublishing = false }

        do {
            let supabaseToken = try await supabaseAuth.accessTokenForBackend()
            let youtubeToken = try await youtube.accessTokenForPublishing()
            let result = try await RenderShortsClient.publish(
                sourceURL: sourceURL,
                startSeconds: trimStart,
                endSeconds: trimEnd,
                title: exportTitle.trimmingCharacters(in: .whitespacesAndNewlines),
                description: publishDescription,
                privacyStatus: privacyStatus,
                madeForKids: madeForKids,
                supabaseAccessToken: supabaseToken,
                youtubeAccessToken: youtubeToken
            )
            publishStatusMessage = "Publicado (\(result.privacyStatus)): \(result.watchURL.absoluteString)"
        } catch {
            publishErrorMessage = error.localizedDescription
        }
    }

    private static func makeVerticalVideoComposition(
        for asset: AVAsset
    ) async throws -> AVMutableVideoComposition {
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else {
            throw EditorError.cannotReadVideo
        }

        let naturalSize = try await track.load(.naturalSize)
        let preferredTransform = try await track.load(.preferredTransform)
        let orientedBounds = CGRect(origin: .zero, size: naturalSize)
            .applying(preferredTransform)
            .standardized
        guard orientedBounds.width > 0, orientedBounds.height > 0 else {
            throw EditorError.cannotPrepareVideo
        }

        let renderSize = CGSize(width: 1080, height: 1920)
        let scale = max(
            renderSize.width / orientedBounds.width,
            renderSize.height / orientedBounds.height
        )
        let xOffset = (renderSize.width - orientedBounds.width * scale) / 2
        let yOffset = (renderSize.height - orientedBounds.height * scale) / 2
        let transform = CGAffineTransform(
            a: preferredTransform.a * scale,
            b: preferredTransform.b * scale,
            c: preferredTransform.c * scale,
            d: preferredTransform.d * scale,
            tx: (preferredTransform.tx - orientedBounds.minX) * scale + xOffset,
            ty: (preferredTransform.ty - orientedBounds.minY) * scale + yOffset
        )

        let duration = try await asset.load(.duration)
        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: track)
        layerInstruction.setTransform(transform, at: .zero)

        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: duration)
        instruction.layerInstructions = [layerInstruction]

        let composition = AVMutableVideoComposition()
        composition.renderSize = renderSize
        composition.frameDuration = CMTime(value: 1, timescale: 30)
        composition.instructions = [instruction]
        return composition
    }

    private static func timeLabel(_ seconds: Double) -> String {
        let value = Int(seconds.rounded())
        return String(format: "%02d:%02d", value / 60, value % 60)
    }
}

private struct ImportedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            let extensionName = received.file.pathExtension.isEmpty ? "mov" : received.file.pathExtension
            let destination = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension(extensionName)
            try FileManager.default.copyItem(at: received.file, to: destination)
            return ImportedMovie(url: destination)
        }
    }
}

private enum EditorError: LocalizedError {
    case cannotReadVideo
    case invalidDuration
    case invalidRange
    case cannotPrepareVideo
    case exportUnavailable
    case exportFailed
    case exportCancelled

    var errorDescription: String? {
        switch self {
        case .cannotReadVideo: "Não foi possível abrir o vídeo selecionado."
        case .invalidDuration: "O vídeo precisa de ter pelo menos dois segundos."
        case .invalidRange: "O intervalo de corte é inválido."
        case .cannotPrepareVideo: "Não foi possível preparar a prévia vertical do vídeo."
        case .exportUnavailable: "O iOS não conseguiu preparar o exportador MP4."
        case .exportFailed: "A exportação do corte falhou."
        case .exportCancelled: "A exportação foi cancelada."
        }
    }

}

private struct TrimRangeTimeline: View {
    @Binding var start: Double
    @Binding var end: Double
    let duration: Double

    @State private var startDragOrigin: Double?
    @State private var endDragOrigin: Double?

    private let handleWidth: CGFloat = 24

    var body: some View {
        GeometryReader { geometry in
            let usableWidth = max(geometry.size.width - handleWidth, 1)
            let startX = handleWidth / 2 + CGFloat(start / duration) * usableWidth
            let endX = handleWidth / 2 + CGFloat(end / duration) * usableWidth

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(StudioTheme.panelRaised)
                    .frame(height: 8)
                    .padding(.horizontal, handleWidth / 2)

                Capsule()
                    .fill(StudioTheme.violet)
                    .frame(width: max(endX - startX, 0), height: 8)
                    .offset(x: startX)

                timelineHandle
                    .offset(x: startX - handleWidth / 2)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let origin = startDragOrigin ?? start
                                startDragOrigin = origin
                                let offset = Double(value.translation.width / usableWidth) * duration
                                start = min(max(origin + offset, 0), max(0, end - 1))
                            }
                            .onEnded { _ in startDragOrigin = nil }
                    )
                    .accessibilityLabel("Início do corte")
                    .accessibilityValue(Self.timeLabel(start))

                timelineHandle
                    .offset(x: endX - handleWidth / 2)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let origin = endDragOrigin ?? end
                                endDragOrigin = origin
                                let offset = Double(value.translation.width / usableWidth) * duration
                                end = min(max(origin + offset, start + 1), duration)
                            }
                            .onEnded { _ in endDragOrigin = nil }
                    )
                    .accessibilityLabel("Fim do corte")
                    .accessibilityValue(Self.timeLabel(end))
            }
            .frame(height: 40)
            .contentShape(Rectangle())
        }
        .frame(height: 40)
    }

    private var timelineHandle: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(StudioTheme.lavender)
            .frame(width: handleWidth, height: 34)
            .overlay {
                Capsule()
                    .fill(.white)
                    .frame(width: 3, height: 16)
            }
    }

    private static func timeLabel(_ seconds: Double) -> String {
        let value = Int(seconds.rounded())
        return String(format: "%02d:%02d", value / 60, value % 60)
    }
}
