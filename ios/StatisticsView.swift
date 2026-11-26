import SwiftUI

struct StatisticsView: View {
    let openConnections: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Spacer(minLength: 10)

            Image(systemName: "chart.xyaxis.line")
                .font(.system(size: 39, weight: .medium))
                .foregroundStyle(StudioTheme.lavender)

            Text("Estatísticas")
                .font(.largeTitle.weight(.heavy))
                .foregroundStyle(.white)

            StudioCard {
                VStack(spacing: 12) {
                    Image(systemName: "chart.bar.fill")
                        .font(.system(size: 30))
                        .foregroundStyle(StudioTheme.lavender)
                    Text("Sem dados para apresentar")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                    Text("As estatísticas só aparecem quando uma conta é ligada e a plataforma disponibiliza os dados através das APIs oficiais.")
                        .font(.caption)
                        .foregroundStyle(StudioTheme.secondaryText)
                        .multilineTextAlignment(.center)
                    StudioPrimaryButton(title: "Começar", action: openConnections)
                        .frame(maxWidth: 130)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .studioBackground()
    }
}
