# Licenciamento e Procedência de Assets (`LICENSE-ASSETS.md`)

Este documento descreve a origem, a situação de licenciamento e as pendências de comprovação de todos os recursos visuais e sonoros relacionados ao aplicativo **Relaxa e Dorme**.

> **Importante:** A licença **GPL-3.0-only** definida em [`LICENSE`](LICENSE) aplica-se **somente ao código-fonte** deste repositório público. Os assets proprietários e de terceiros listados na Seção 2 **não estão incluídos** neste repositório.

---

## 1. Recursos Incluídos no Repositório Público

| Recurso | Localização no repositório | Origem | Situação de Licenciamento |
| :--- | :--- | :--- | :--- |
| **Ícones vetoriais de interface** | `app/src/main/res/drawable/ic_*.xml` | Material Symbols / Material Icons (Google) e recursos XML do projeto | Ícones Material geralmente licenciados sob **Apache License 2.0**; arquivos XML autorais do projeto acompanham o repositório sob **GPL-3.0-only**. |
| **Placeholders neutros de launcher e splash** | `app/src/main/res/mipmap-*/ic_launcher*.png`, `app/src/main/res/drawable-*/ic_splash_ghosty.png` | Gerados especificamente como placeholders neutros para compilação comunitária | Recursos neutros temporários incluídos para permitir build funcional sem assets proprietários. |
| **Arquivos de configuração XML e strings** | `app/src/main/res/values*/`, `app/src/main/res/xml/` | Projeto Relaxa e Dorme (mTaaz) | Distribuídos com o código-fonte sob **GPL-3.0-only**. |

---

## 2. Assets Excluídos do Repositório Público

Os ativos abaixo fazem parte da distribuição oficial privada e **não estão presentes** em `RelaxaeDorme-Public`. Eles não são cobertos pela licença GPL-3.0-only deste repositório.

### 2.1. Ghosty (Ilustrações, Launcher e Splash Oficiais)
- **Caminhos na versão oficial:**
  - `app/src/main/assets/ghosty/` (16 ilustrações PNG)
  - `app/src/main/res/mipmap-*/ic_launcher*.png` (15 PNGs originais)
  - `app/src/main/res/drawable-*/ic_splash_ghosty.png` (5 PNGs originais)
- **Origem:** Ilustrações proprietárias do projeto Relaxa e Dorme.
- **Situação de licenciamento:** **Proprietário (sem licença aberta concedida)**.
- **Comportamento na edição pública:** Na ausência dos PNGs em `assets/ghosty/`, o componente `GhostyBuddy` exibe automaticamente um fallback vetorial seguro, e os recursos de launcher/splash usam os placeholders neutros.
- **Pendência:** Caso haja intenção futura de abrir ou licenciar separadamente essas ilustrações, definir explicitamente a autoria completa e os termos de uso antes de qualquer publicação.

### 2.2. Sons Ambientes
- **Caminho na versão oficial:** `app/src/main/assets/sounds/` (18 arquivos `.ogg` em 5 categorias).
- **Origem:** **Pixabay**.
- **Licença:** **Pixabay Content License**, conforme os termos aplicáveis a cada conteúdo (não classificar genericamente os arquivos como CC0).
- **Quantidade e uso:** 18 arquivos `.ogg`, destinados à reprodução individual e no mixer do aplicativo.
- **Atribuição:** Não obrigatória em regra pela licença geral.
- **Referências:**
  - `https://pixabay.com/service/terms/`
  - `https://pixabay.com/service/license-summary/`
- **Situação atual:** Mantidos fora do repositório público.
- **Pendência explícita:** Confirmar se a incorporação e redistribuição dos arquivos `.ogg` dentro do APK, com possibilidade de reprodução individual, é permitida pelos termos aplicáveis. Não se declara que a redistribuição está automaticamente autorizada. Consulte os detalhes individuais em [`AUDIO_LICENSES.md`](AUDIO_LICENSES.md).

### 2.3. Músicas (Faixas Demo e Catálogo Completo)
- **Caminhos na versão oficial:**
  - `app/src/main/assets/demo_music/` (4 faixas demo `.ogg` embarcadas no APK oficial)
  - Bucket remoto configurado via `R2_BASE_PUBLIC_URL` + `catalog.json` (catálogo completo)
- **Origem:** Faixas geradas pelo usuário (**mTaaz**) utilizando o **Flow Music**.
- **Declaração de procedência e autorização:** O responsável pelo projeto **Relaxa e Dorme** confirmou expressamente que verificou as condições de uso e distribuição das músicas geradas no **Flow Music** e que a situação está regular para a utilização pretendida no aplicativo.
- **Situação no repositório público:** Excluídas do repositório público (`RelaxaeDorme-Public`) por separação de assets.

### 2.4. Capas de Álbuns e Faixas
- **Caminhos na versão oficial:**
  - `app/src/main/assets/covers/nordic_fantasy.jpg` (capa local)
  - Capas remotas referenciadas no `catalog.json`
- **Origem:** Assets visuais proprietários do catálogo oficial.
- **Situação atual:** Excluídas do repositório público.
- **Comportamento na edição pública:** Na ausência da capa local e de catálogo remoto, a interface exibe fallback visual sem falhas.
- **Pendência explícita:** Documentar a ferramenta/origem de criação e os termos de licenciamento aplicáveis antes de qualquer redistribuição pública das imagens.
