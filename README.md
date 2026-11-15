# 🌙 Relaxa e Dorme (Edição Pública / Comunitária)

**Relaxa e Dorme** é um aplicativo Android de música ambiente, sons da natureza e relaxamento, desenvolvido em **Kotlin** e **Jetpack Compose**, com reprodução em segundo plano via **AndroidX Media3 (ExoPlayer + MediaSessionService)**.

Esta é a **edição pública de código aberto** do projeto, licenciada sob **GNU General Public License v3.0 (`GPL-3.0-only`)**.

---

## 📦 O que está neste repositório vs. o que foi separado

Para respeitar o licenciamento e a procedência de arquivos de mídia e ilustrações, este repositório público contém **exclusivamente o código-fonte, testes unitários, recursos vetoriais de interface e placeholders neutros**:

- **Incluído:**
  - Todo o código-fonte Kotlin (UI em Compose/Material 3, Player, Mixer, Repositórios, Cache, DataStore, Media3).
  - Suíte completa de testes unitários (`62` testes).
  - Ícones vetoriais XML de interface e suporte multilíngue (**Português BR**, **Inglês** e **Espanhol**).
  - Placeholders neutros para ícone de launcher e splash screen.
- **Excluído (mantido fora do repositório público):**
  - Ilustrações proprietárias da mascote **Ghosty** (`assets/ghosty/`, launcher e splash oficiais).
  - Os 18 sons ambientes (`assets/sounds/`).
  - As 4 músicas demo locais (`assets/demo_music/`).
  - Capas proprietárias (`assets/covers/`) e o arquivo `catalog.json` oficial.
  - A URL de produção do Cloudflare R2, credenciais e chaves de assinatura.

### Como o aplicativo se comporta sem os assets privados?

O código foi projetado para funcionar de forma independente e sem crashes mesmo sem nenhum asset proprietário ou URL remota configurada:

- **Ghosty:** exibe um fallback vetorial seguro quando os PNGs não estão presentes.
- **Sons Ambientes:** verifica a existência física de cada `.ogg` e apresenta um estado vazio amigável quando não há arquivos locais.
- **Músicas / Capas:** inicializa sem erros com `BuildConfig.R2_BASE_PUBLIC_URL` vazio (`""`), exibindo estado vazio informativo e fallback visual para capas ausentes.
- **Mixer:** continua acessível e operacional.

---

## ✨ Funcionalidades Principais

- **Biblioteca Musical por Álbuns:** catálogo JSON remoto configurável, streaming, download para reprodução offline, favoritos, histórico, mini player e player expandido.
- **Sons Ambientes e Mixer:** suporte a até 8 camadas simultâneas em loop contínuo, com controle independente de volume por som e cenas salvas.
- **Recursos de Descanso:** timer de desligamento automático com fade-out gradual e reprodução contínua em segundo plano.
- **Privacidade:** sem cadastro, sem anúncios e sem rastreamento; preferências e histórico armazenados localmente via DataStore.
- **Idiomas:** Português (BR), Inglês e Espanhol.

---

## 🛠️ Arquitetura e Tecnologias

| Tecnologia | Utilização |
| :--- | :--- |
| **Kotlin** (`2.2.10`) | Linguagem principal |
| **Jetpack Compose + Material 3** | Interface declarativa |
| **AndroidX Navigation Compose** | Navegação entre telas |
| **AndroidX Media3 (ExoPlayer / MediaSession)** (`1.11.1`) | Reprodução local/remota e em segundo plano |
| **DataStore Preferences** (`1.1.3`) | Persistência local de preferências e histórico |
| **Kotlinx Serialization** (`1.8.0`) | Parse do catálogo JSON |
| **AGP** (`9.3.3`) | Build Android (`minSdk 26` / `targetSdk 37`) |

---

## 🚀 Como Compilar e Testar

### Pré-requisitos
- Android Studio recente com **Android SDK** configurado (`API 26+` / `compileSdk 37`).
- JDK 11 ou superior.

### Comandos (na raiz do projeto)

```bash
# Executar testes unitários
./gradlew testDebugUnitTest

# Gerar APK debug (edição comunitária)
./gradlew assembleDebug

# Executar análise estática (Lint)
./gradlew lintDebug
```

*(No Windows, utilize `.\gradlew.bat`.)*

### Configuração Opcional de Catálogo Remoto (R2 / HTTP)

Por padrão, `BuildConfig.R2_BASE_PUBLIC_URL` é compilado como string vazia (`""`). Caso deseje testar com um servidor/bucket próprio compatível, forneça a URL temporariamente sem gravá-la no código-fonte:

```bash
./gradlew assembleDebug -PR2_BASE_PUBLIC_URL=https://seu-endpoint.exemplo
```

Ou via variável de ambiente `R2_BASE_PUBLIC_URL`.

---

## 📄 Licenciamento e Documentação Relacionada

- [`LICENSE`](LICENSE) — Texto oficial completo da licença **GPL-3.0-only** (aplicável ao código-fonte).
- [`NOTICE`](NOTICE) — Avisos de copyright, separação de assets e atribuições de bibliotecas de terceiros.
- [`LICENSE-ASSETS.md`](LICENSE-ASSETS.md) — Situação detalhada de licenciamento, origem e pendências de cada categoria de asset.
- [`AUDIO_LICENSES.md`](AUDIO_LICENSES.md) — Registro técnico dos 18 sons ambientes, 4 faixas demo e pendências de comprovação.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Regras de contribuição e verificações obrigatórias.

---

## 🧑‍💻 Autoria

Desenvolvido por **mTaaz** com apoio de ferramentas de inteligência artificial no processo de desenvolvimento, arquitetura e revisão.
