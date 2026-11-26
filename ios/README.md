# Short Planet Stream — iOS SwiftUI e preparação para iPhone (.ipa)

O diretório `ios/` contém uma aplicação SwiftUI nativa com as sete telas de referência e um projeto Xcode compilável. A implementação Android em Kotlin permanece separada e inalterada.

## YouTube e edição de vídeo

A aplicação usa Google Sign-In e YouTube Data API v3 para autenticar a conta, encontrar o canal, listar vídeos enviados e mostrar transmissões ao vivo/gravadas. Ativa a YouTube Data API v3 no projeto Google Cloud associado ao OAuth e configura:

1. Cria um cliente OAuth do tipo **iOS** com o Bundle ID `com.shortplanetstream.app`.
2. Copia o Client ID e o URL scheme invertido para `ios/IntegrationConfig.xcconfig` (`GOOGLE_IOS_CLIENT_ID` e `GOOGLE_REVERSED_CLIENT_ID`). O ficheiro contém apenas valores de exemplo; não coloques nele segredos.
3. Configura o ecrã de consentimento OAuth e adiciona as contas de teste, se a aplicação ainda estiver em modo de testes.

A app pede apenas o scope `youtube.readonly`. A API dá acesso a metadados, não ao ficheiro de vídeo. Depois de escolheres um vídeo ou live na biblioteca, importa do iPhone o original correspondente que tens autorização para editar. O editor mostra a prévia vertical, permite marcar o início e o fim e exporta o trecho em MP4 9:16 sem alterar o original.

## Supabase no iOS

O target iOS usa o SDK oficial `supabase-swift`. A autenticação Supabase é independente do Google Sign-In usado para aceder ao YouTube. É possível continuar a explorar sem sessão; inicia sessão ou cria uma conta em **Conectar** para guardar o perfil.

Guarda a URL e a chave **Publishable** localmente em `ios/IntegrationConfig.local.xcconfig`:

```xcconfig
SUPABASE_URL = https:/$()/SEU_PROJECT_ID.supabase.co
SUPABASE_PUBLISHABLE_KEY = sb_publishable_SUA_CHAVE
```

O fragmento `/$()/` evita que o parser `.xcconfig` interprete `//` da URL como comentário; o Xcode resolve o valor como `https://SEU_PROJECT_ID.supabase.co`. Esse ficheiro é ignorado pelo Git. Não uses nele uma chave `service_role`, `sb_secret_*` ou outro segredo administrativo. Os valores são passados ao target pelo `Info.plist`; a sessão Auth é persistida pelo SDK no Keychain.

Ao autenticar, a app verifica primeiro se `public.usuarios` permite ler as colunas `id` e `email`; só então tenta inserir/atualizar o perfil com o ID proveniente da sessão Auth. A operação não cria tabelas nem contorna RLS. Configura no Supabase a tabela, os campos e as políticas RLS que autorizem o próprio usuário. A app não escreve ainda nas outras tabelas planejadas nem armazena tokens de plataformas. O Storage permanece privado e preparado para integração futura; TikTok continua usando a folha oficial de partilha do iOS.

## TikTok

O corte exportado pode ser enviado pelo menu de partilha do iOS; escolhe TikTok e confirma a publicação na aplicação. Isto não liga uma conta TikTok nem publica automaticamente. Publicação direta requer uma aplicação TikTok aprovada e um backend seguro; credenciais ou segredos do Android não devem ser reutilizados no cliente iOS.

Firebase, IA e outras integrações Android não são configuradas por esta implementação iOS.

## Compilar no macOS

Abre `ios/ShortPlanetStream.xcodeproj` no Xcode, escolhe o scheme `ShortPlanetStream` e executa num simulador ou dispositivo. Também podes compilar o simulador na linha de comandos:

```bash
xcodebuild -project ios/ShortPlanetStream.xcodeproj \
  -scheme ShortPlanetStream \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

## Docker e Android

Os comandos Docker continuam destinados ao projeto Android:

```bash
# Executar testes do estúdio Android
docker compose -f docker/docker-compose.yml run --rm studio-builder test

# Preparar artefactos iOS / verificar Fastlane
docker compose -f docker/docker-compose.yml run --rm ios-prep

# Gerar APK Android no container
docker compose -f docker/docker-compose.yml run --rm studio-builder apk
```

## Gerar um `.ipa` no GitHub Actions

Faz push das alterações em `ios/` para a branch `main` ou executa o workflow **Build iOS IPA** manualmente. O runner macOS compila e publica o artefacto `ShortPlanetStream-iOS-IPA`. A assinatura para distribuição continua a exigir a configuração de certificados e provisioning da Apple.
