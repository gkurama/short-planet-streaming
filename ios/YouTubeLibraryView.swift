import SwiftUI

struct YouTubeLibraryView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: YouTubeConnectionStore
    let initialFilter: YouTubeLibraryFilter
    let openConnections: () -> Void
    let selectVideo: (YouTubeVideo) -> Void

    @State private var filter: YouTubeLibraryFilter
    @State private var searchText = ""

    init(
        store: YouTubeConnectionStore,
        initialFilter: YouTubeLibraryFilter,
        openConnections: @escaping () -> Void,
        selectVideo: @escaping (YouTubeVideo) -> Void
    ) {
        self.store = store
        self.initialFilter = initialFilter
        self.openConnections = openConnections
        self.selectVideo = selectVideo
        _filter = State(initialValue: initialFilter)
    }

    private var filteredVideos: [YouTubeVideo] {
        store.videos.filter { video in
            let matchesType = filter == .all || (filter == .lives ? video.isLive : !video.isLive)
            let matchesSearch = searchText.isEmpty
                || video.title.localizedCaseInsensitiveContains(searchText)
                || video.channelName.localizedCaseInsensitiveContains(searchText)
            return matchesType && matchesSearch
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let channel = store.channel {
                        HStack(spacing: 12) {
                            AsyncImage(url: channel.thumbnailURL) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Image(systemName: "play.rectangle.fill")
                                    .foregroundStyle(StudioTheme.lavender)
                            }
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 3) {
                                Text(channel.title)
                                    .font(.headline.weight(.bold))
                                    .foregroundStyle(.white)
                                Text(channel.handle.isEmpty ? "Canal ligado" : channel.handle)
                                    .font(.caption)
                                    .foregroundStyle(StudioTheme.secondaryText)
                            }
                            Spacer()
                            Button {
                                Task { await store.refresh() }
                            } label: {
                                Image(systemName: "arrow.clockwise")
                                    .foregroundStyle(StudioTheme.lavender)
                            }
                            .disabled(store.isLoading)
                            .accessibilityLabel("Atualizar vídeos")
                        }
                        .padding(.horizontal, 18)
                        .padding(.top, 8)

                        Picker("Tipo de conteúdo", selection: $filter) {
                            ForEach(YouTubeLibraryFilter.allCases) { item in
                                Text(item.rawValue).tag(item)
                            }
                        }
                        .pickerStyle(.segmented)
                        .tint(StudioTheme.violet)
                        .padding(.horizontal, 18)

                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .foregroundStyle(StudioTheme.secondaryText)
                            TextField("Filtrar por título...", text: $searchText)
                                .font(.subheadline)
                                .foregroundStyle(.white)
                        }
                        .padding(12)
                        .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 18)

                        Text("A biblioteca é para consultar e abrir os vídeos no YouTube. Para editar, usa um arquivo original escolhido no aparelho.")
                            .font(.caption)
                            .foregroundStyle(StudioTheme.secondaryText)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)

                        if filteredVideos.isEmpty && !store.isLoading {
                            StudioCard {
                                VStack(spacing: 9) {
                                    Image(systemName: filter == .lives ? "dot.radiowaves.left.and.right" : "video.slash")
                                        .font(.title2)
                                        .foregroundStyle(StudioTheme.lavender)
                                    Text(emptyLibraryMessage)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.white)
                                    Text("Confirma se a YouTube Data API v3 está ativa e se a conta tem um canal.")
                                        .font(.caption)
                                        .foregroundStyle(StudioTheme.secondaryText)
                                        .multilineTextAlignment(.center)
                                }

                            }
                            .padding(.horizontal, 18)
                        }

                        LazyVStack(spacing: 12) {
                            ForEach(filteredVideos) { video in
                                YouTubeVideoRow(video: video) {
                                    selectVideo(video)
                                    dismiss()
                                }
                            }
                        }
                        .padding(.horizontal, 18)

                        if store.nextPageToken != nil {
                            StudioPrimaryButton(
                                title: store.isLoading ? "A carregar..." : "Carregar mais",
                                systemImage: "arrow.down"
                            ) {
                                Task { await store.loadMore() }
                            }
                            .disabled(store.isLoading)
                            .padding(.horizontal, 18)
                        }
                    } else {
                        StudioCard {
                            VStack(spacing: 12) {
                                Image(systemName: "play.rectangle.fill")
                                    .font(.system(size: 30))
                                    .foregroundStyle(.red)
                                Text("Liga a tua conta YouTube")
                                    .font(.headline.weight(.bold))
                                    .foregroundStyle(.white)
                                Text("A biblioteca mostra os vídeos do canal e as transmissões ao vivo ou gravadas.")
                                    .font(.subheadline)
                                    .foregroundStyle(StudioTheme.secondaryText)
                                    .multilineTextAlignment(.center)
                                StudioPrimaryButton(title: "Entrar com Google", action: openConnections)
                                    .frame(maxWidth: 190)
                            }
                        }
                        .padding(.horizontal, 18)
                        .padding(.top, 28)
                    }

                    if store.isLoading {
                        ProgressView("A sincronizar com YouTube...")
                            .tint(StudioTheme.lavender)
                            .padding(.vertical, 12)
                    }

                    if let error = store.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(Color.orange)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 22)
                    }
                }
                .padding(.vertical, 12)
            }
            .studioBackground()
            .navigationTitle("Vídeos e lives")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                        .foregroundStyle(StudioTheme.lavender)
                }
            }
        }
    }

    private var emptyLibraryMessage: String {
        switch filter {
        case .all: "Sem vídeos ou transmissões encontradas"
        case .videos: "Sem vídeos encontrados"
        case .lives: "Sem transmissões encontradas"
        }
    }
}

private struct YouTubeVideoRow: View {
    let video: YouTubeVideo
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                AsyncImage(url: video.thumbnailURL) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    RoundedRectangle(cornerRadius: 9)
                        .fill(StudioTheme.panelRaised)
                        .overlay {
                            Image(systemName: video.isLive ? "dot.radiowaves.left.and.right" : "play.fill")
                                .foregroundStyle(StudioTheme.lavender)
                        }
                }
                .frame(width: 112, height: 68)
                .clipShape(RoundedRectangle(cornerRadius: 9))
                .overlay(alignment: .bottomTrailing) {
                    Text(video.duration)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 3)
                        .background(video.isLive ? Color.red : Color.black.opacity(0.8), in: Capsule())
                        .padding(4)
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text(video.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(video.channelName) · \(video.viewCount) visualizações")
                        .font(.caption2)
                        .foregroundStyle(StudioTheme.secondaryText)
                        .lineLimit(1)
                    if video.isLive {
                        Text("TRANSMISSÃO")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(.red)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(StudioTheme.lavender)
            }
            .padding(10)
            .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}
