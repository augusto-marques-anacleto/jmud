# jMud

Cliente MUD para Android feito por e para pessoas cegas, com acessibilidade como requisito central.

## Recursos

- Lista de personagens com login automático e comandos pós-conexão
- Conexão manual a qualquer servidor por endereço e porta
- Envio de vários comandos de uma vez com separador configurável (padrão: espaço, arroba, espaço)
- Triggers (gatilhos) com vários tipos de busca, padrões com variáveis, históricos nomeados e sons
- Temporizadores com escopo por MUD ou por personagem
- Sintetizador de voz integrado (mecanismo, voz, velocidade, tom e volume configuráveis)
- Suporte a MSP (MUD Sound Protocol) com pacotes de sons e cache de downloads
- Gerenciador de pastas de sons nas Configurações
- Revisão do histórico pelo teclado físico
- Ajuda completa em 11 páginas, com tela de boas-vindas na primeira abertura
- Logs de sessão com retenção configurável
- Backup e restauração de todos os dados
- Atualizador integrado

## Requisitos

Android 7.0 (API 24) ou superior, cerca de 50 MB livres e conexão com a internet. Recomendado leitor de telas (TalkBack ou Jieshuo) e um sintetizador de voz instalado.

## Download

Baixe o APK mais recente na [página de releases](https://github.com/augusto-marques-anacleto/jmud/releases/latest). O aplicativo avisa automaticamente quando há uma nova versão.

Sugestões e problemas: abra uma issue aqui no GitHub ou escreva para augustoanacletoprojetos@gmail.com.

## Compilação

Projeto Android padrão (Kotlin + Jetpack Compose, JDK 17). Abra no Android Studio ou compile pela linha de comando:

```
gradlew assembleDebug
gradlew test
```

O build de release exige uma keystore própria referenciada em `keystore/keystore.properties` (não incluída no repositório):

```
storeFile=keystore/jmud-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Arquitetura

O código fica em `app/src/main/java/br/com/augusto/jmud/`, organizado em quatro camadas:

- `domain/` — modelos de dados puros: `MudCharacter`, `MudTrigger`, `MudTimer`, `Scope`.
- `data/local/` — persistência em SharedPreferences (`cmud_data`): repositórios de personagens, triggers, temporizadores e configurações.
- `data/network/` — a conexão: `MudConnectionManager` (socket, máquina de estados telnet, decodificação de charset, limpeza de ANSI), `MudEvent` (eventos emitidos por SharedFlow) e `MudService` (serviço em primeiro plano que mantém a conexão viva com a tela apagada).
- `util/` — motores independentes de Android sempre que possível: `MspParser` (protocolo MSP), `TriggerEngine` (correspondência e expansão de variáveis), `TTSManager`, `MudAudioManager` (ExoPlayer e SoundPool), `LogManager` (gravação em thread própria), `SoundPackInstaller`, `UpdateManager`, `BackupManager`, `AppStorage`.
- `ui/` — Jetpack Compose: `MudViewModel` (estado central único), telas em `ui/screens/` e componentes reutilizáveis em `ui/components/`.

Fluxo principal: `MudConnectionManager` emite `MudEvent` → `MudViewModel` coleta, passa cada linha pelo `MspParser` e pelo `TriggerEngine`, alimenta o histórico, o TTS e os sons → as telas Compose observam o estado do ViewModel.

## Regras do projeto

Estas regras existem por causa do público do aplicativo e não são opcionais:

- **Acessibilidade primeiro.** Campos de texto usam EditText nativo via `AppTextField` (compatibilidade com Jieshuo); os demais componentes declaram papéis semânticos explícitos. Toda mudança de interface deve ser testada com leitor de telas.
- **Nenhuma string de interface no código.** Tudo em `res/values/strings.xml` (português, padrão) e `res/values-en/strings.xml` (inglês), incluindo plurais.
- **Nunca travar por causa do servidor.** Dados vindos da rede são tratados como hostis: parsers têm fallback e exceções não derrubam a sessão.
- **Privacidade.** O aplicativo não coleta dados nem fala com nenhum servidor além do MUD escolhido, do GitHub (atualizações) e dos downloads de pacotes de sons pedidos pelo usuário.
- Commits e releases em português, com acentuação correta.

## Testes

Os motores de lógica pura têm testes unitários em `app/src/test/`: `MspParserTest`, `TriggerEngineTest`, `SoundPackInstallerTest` e `FolderNamesTest`. Rode com `gradlew test`. O CI executa os testes em todo push e pull request.

## Processo de release

1. Aumentar `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Commit das mudanças e `gradlew assembleRelease`.
3. Criar o release no GitHub (`vX.Y`) anexando o APK com o nome `jMud.apk`.
4. Atualizar `update.json` (versionCode, versionName, url do release, changelog em português) e fazer commit — o atualizador integrado lê este arquivo direto do branch `main`.

## Dados do usuário

- Preferências, personagens, triggers e temporizadores: SharedPreferences do aplicativo (exportáveis pelo backup em arquivo `.jmud`).
- Pastas de sons e logs: `Documents/jMud/` no armazenamento interno do aparelho.
- Atenção: o arquivo de backup contém as senhas dos personagens em texto puro; oriente o usuário a guardá-lo com cuidado. A senha nunca é gravada nos logs de sessão (é mascarada).
