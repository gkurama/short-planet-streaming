import SwiftUI

struct TemplatesView: View {
    let onApply: (StudioTemplate) -> Void
    @State private var selectedCategory = "Todos"
    @State private var previewTemplate: StudioTemplate?

    private let categories = ["Todos", "Gaming", "Educativo", "Entretenimento"]
    private let templates = [
        StudioTemplate(
            name: "Legenda Dinâmica",
            category: "Gaming",
            banner: "LEGENDA\nDINÂMICA",
            colors: [Color(red: 0.60, green: 0.12, blue: 0.98), Color(red: 0.74, green: 0.10, blue: 0.95)]
        ),
        StudioTemplate(
            name: "React Rápido",
            category: "Entretenimento",
            banner: "REACT RÁPIDO",
            colors: [Color(red: 0.16, green: 0.23, blue: 0.89), Color(red: 0.48, green: 0.12, blue: 0.92)]
        ),
        StudioTemplate(
            name: "Dica de Ouro",
            category: "Educativo",
            banner: "DICA DE OURO",
            colors: [Color(red: 1.0, green: 0.56, blue: 0.13), Color(red: 0.96, green: 0.15, blue: 0.26)]
        ),
        StudioTemplate(
            name: "Top 5",
            category: "Entretenimento",
            banner: "TOP 5",
            colors: [Color(red: 0.12, green: 0.78, blue: 0.85), Color(red: 0.12, green: 0.31, blue: 0.86)]
        )
    ]

    private var filteredTemplates: [StudioTemplate] {
        selectedCategory == "Todos"
            ? templates
            : templates.filter { $0.category == selectedCategory }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 15) {
                Text("Templates")
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(.white)
                    .padding(.top, 15)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories, id: \.self) { category in
                            Button {
                                selectedCategory = category
                            } label: {
                                Text(category)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 13)
                                    .padding(.vertical, 9)
                                    .background(
                                        selectedCategory == category ? StudioTheme.violet : StudioTheme.panel,
                                        in: Capsule()
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 13) {
                    ForEach(filteredTemplates) { template in
                        TemplateCard(template: template) {
                            previewTemplate = template
                        }
                    }
                }
                .padding(.bottom, 20)
            }
            .padding(.horizontal, 16)
        }
        .studioBackground()
        .sheet(item: $previewTemplate) { template in
            TemplateDetailSheet(template: template) {
                onApply(template)
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }
}

struct StudioTemplate: Identifiable {
    let name: String
    let category: String
    let banner: String
    let colors: [Color]
    var id: String { name }
}

private struct TemplateCard: View {
    let template: StudioTemplate
    let showDetails: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                LinearGradient(colors: template.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                Text(template.banner)
                    .font(.system(size: 16, weight: .heavy))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.25), radius: 3)
                    .padding(8)
            }
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 13))

            Text(template.name)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Text("Pré-visualização")
                .font(.caption)
                .foregroundStyle(StudioTheme.secondaryText)
            StudioPrimaryButton(title: "Ver detalhes", action: showDetails)
                .padding(.top, 1)
        }
        .padding(9)
        .background(StudioTheme.panel, in: RoundedRectangle(cornerRadius: 17))
    }
}

private struct TemplateDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let template: StudioTemplate
    let apply: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            RoundedRectangle(cornerRadius: 18)
                .fill(LinearGradient(colors: template.colors, startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay {
                    Text(template.banner)
                        .font(.title2.weight(.heavy))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                }
                .frame(height: 160)

            Text(template.name)
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)

            StudioPrimaryButton(title: "Usar template") {
                apply()
                dismiss()
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(StudioTheme.background.ignoresSafeArea())
    }
}
