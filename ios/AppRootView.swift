import SwiftUI
import UIKit

struct AppRootView: View {
    @State private var enteredStudio = false
    @StateObject private var youtube = YouTubeConnectionStore()
    @StateObject private var supabaseAuth = SupabaseAuthStore()

    var body: some View {
        Group {
            if enteredStudio {
                StudioTabView(youtube: youtube, supabaseAuth: supabaseAuth)
            } else {
                ConnectAccountsView(
                    onContinue: { enteredStudio = true },
                    youtube: youtube,
                    supabaseAuth: supabaseAuth
                )
            }
        }
        .animation(.easeInOut(duration: 0.2), value: enteredStudio)
        .task {
            await supabaseAuth.restoreSession()
        }
        .onChange(of: youtube.isConnected) { _, isConnected in
            if isConnected {
                enteredStudio = true
            }
        }
    }
}

private enum StudioTab: Hashable {
    case home
    case editor
    case shorts
    case templates
    case statistics
    case profile
}

private struct StudioTabView: View {
    @ObservedObject var youtube: YouTubeConnectionStore
    @ObservedObject var supabaseAuth: SupabaseAuthStore
    @State private var selectedTab: StudioTab = .home
    @State private var showingConnections = false
    @State private var showingLibrary = false
    @State private var didPresentInitialLibrary = false
    @State private var libraryFilter: YouTubeLibraryFilter = .all
    @State private var templateForEditor: String?

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeDashboardView(
                openConnections: { showingConnections = true },
                openVideos: { openLibrary(.videos) },
                openLives: { openLibrary(.lives) },
                openEditor: { selectedTab = .editor }
            )
            .tabItem { Label("Início", systemImage: "house.fill") }
            .tag(StudioTab.home)

            EditorView(
                templateName: templateForEditor,
                youtube: youtube,
                supabaseAuth: supabaseAuth,
                openConnections: { showingConnections = true }
            )
                .tabItem { Label("Editor", systemImage: "scissors") }
                .tag(StudioTab.editor)

            ShortsView(openConnections: { showingConnections = true })
                .tabItem { Label("Shorts", systemImage: "play.rectangle.fill") }
                .tag(StudioTab.shorts)

            TemplatesView { template in
                templateForEditor = template.name
                selectedTab = .editor
            }
            .tabItem { Label("Templates", systemImage: "square.grid.2x2.fill") }
            .tag(StudioTab.templates)

            StatisticsView(openConnections: { showingConnections = true })
                .tabItem { Label("Estatísticas", systemImage: "chart.bar.fill") }
                .tag(StudioTab.statistics)

            ProfileView(openConnections: { showingConnections = true }, supabaseAuth: supabaseAuth)
                .tabItem { Label("Perfil", systemImage: "person.crop.circle.fill") }
                .tag(StudioTab.profile)
        }
        .tint(StudioTheme.violet)
        .toolbarBackground(StudioTheme.panelRaised, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .sheet(isPresented: $showingConnections) {
            ConnectAccountsView(onContinue: {
                showingConnections = false
            }, youtube: youtube, supabaseAuth: supabaseAuth)
        }
        .sheet(isPresented: $showingLibrary) {
            YouTubeLibraryView(
                store: youtube,
                initialFilter: libraryFilter,
                openConnections: {
                    showingLibrary = false
                    showingConnections = true
                },
                selectVideo: { video in
                    showingLibrary = false
                    UIApplication.shared.open(video.watchURL)
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .onAppear {
            presentInitialLibraryIfNeeded()
        }
        .onChange(of: youtube.isConnected) { _, isConnected in
            guard isConnected else {
                didPresentInitialLibrary = false
                return
            }
            showingConnections = false
            presentInitialLibraryIfNeeded()
        }
    }

    private func openLibrary(_ filter: YouTubeLibraryFilter) {
        libraryFilter = filter
        showingLibrary = true
    }

    private func presentInitialLibraryIfNeeded() {
        guard youtube.isConnected, !didPresentInitialLibrary else { return }
        didPresentInitialLibrary = true
        openLibrary(.all)
    }
}
