# Registro de Origem e Licenciamento de Áudios (`AUDIO_LICENSES.md`)

Este documento registra a referência técnica, a procedência informada e as pendências jurídicas de todos os áudios utilizados pelo aplicativo **Relaxa e Dorme**.

> **Importante:** Na edição pública (`RelaxaeDorme-Public`), **nenhum arquivo `.ogg` está incluído no repositório**. O aplicativo reconhece dinamicamente a ausência dos arquivos locais, ocultando sons indisponíveis e exibindo estados vazios amigáveis sem falhas.

---

## 1. Sons Ambientes (`app/src/main/assets/sounds/`)

Os 18 sons abaixo são declarados em `SoundCatalog.kt` (`app/src/main/java/com/taaz/relaxaedorme/data/catalog/SoundCatalog.kt`).

- **Origem:** Pixabay
- **Licença:** Pixabay Content License, conforme os termos aplicáveis a cada conteúdo (não classificar genericamente os arquivos como CC0).
- **Quantidade:** 18 arquivos `.ogg`.
- **Uso:** Reprodução individual e no mixer do aplicativo.
- **Atribuição:** Não obrigatória em regra pela licença geral.
- **Referências:**
  - `https://pixabay.com/service/terms/`
  - `https://pixabay.com/service/license-summary/`
- **Pendência explícita:** Confirmar se a incorporação e redistribuição dos arquivos `.ogg` dentro do APK, com possibilidade de reprodução individual, é permitida pelos termos aplicáveis. Não se declara que a redistribuição está automaticamente autorizada.

| ID Estável | Nome do Som | Categoria | Caminho Esperado em `assets/` | Origem | Licença | Situação / Pendência |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `sound_rain_001` | Chuva Suave | `rain` | `sounds/rain/light_rain.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_rain_002` | Chuva Intensa | `rain` | `sounds/rain/heavy_rain.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_rain_003` | Tempestade Suave | `rain` | `sounds/rain/light_storm.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_rain_004` | Tempestade Intensa | `rain` | `sounds/rain/heavy_storm.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_rain_005` | Trovão Distante | `rain` | `sounds/rain/distant_thunder.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_rain_006` | Trovão Forte | `rain` | `sounds/rain/heavy_thunder.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_water_001` | Ondas do Mar | `water` | `sounds/water/ocean_waves.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_water_002` | Correnteza do Rio | `water` | `sounds/water/river.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_water_003` | Cachoeira | `water` | `sounds/water/waterfall.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_forest_001` | Pássaros na Floresta | `forest` | `sounds/forest/forest_birds.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_forest_002` | Grilos Noturnos | `forest` | `sounds/forest/forest_crickets.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_forest_003` | Vento na Floresta | `forest` | `sounds/forest/wind_forest.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_fire_001` | Fogueira de Acampamento | `fire` | `sounds/fire/campfire.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_fire_002` | Lareira Acesa | `fire` | `sounds/fire/fireplace.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_fire_003` | Brasas Crepitantes | `fire` | `sounds/fire/closeup_firebush.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_noise_001` | Ruído Branco | `noise` | `sounds/noise/white_noise.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_noise_002` | Ruído Rosa | `noise` | `sounds/noise/pink_noise.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |
| `sound_noise_003` | Ruído Marrom | `noise` | `sounds/noise/brown_noise.ogg` | Pixabay | Pixabay Content License | Excluído do repo público; pendente verificação de redistribuição no APK |

---

## 2. Músicas Demo Locais (`app/src/main/assets/demo_music/`)

As 4 faixas demo abaixo são mapeadas explicitamente por ID em `RemoteMusicCatalogDto.kt` e `MusicOfflineStorageManager.kt`.

- **Origem:** Geradas pelo usuário (**mTaaz**) no **Flow Music**.
- **Declaração de procedência e autorização:** O responsável pelo **Relaxa e Dorme** confirmou expressamente que verificou as condições de uso e distribuição das músicas geradas no **Flow Music** e que a situação está regular para a utilização pretendida no aplicativo.
- **Situação no repositório público:** Excluídas do repositório público (`RelaxaeDorme-Public`) por separação de assets.

| ID Estável | Título | Álbum | Caminho Esperado em `assets/` | Origem | Situação |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `music_nf_001` | Above the Fjord | `nordic_fantasy` | `demo_music/nordic_fantasy/above_the_fjord.ogg` | Flow Music (gerada pelo usuário) | Autorização confirmada pelo responsável; excluída do repo público |
| `music_nf_002` | Dawn Over Frozen Pine | `nordic_fantasy` | `demo_music/nordic_fantasy/dawn_over_frozen_pine.ogg` | Flow Music (gerada pelo usuário) | Autorização confirmada pelo responsável; excluída do repo público |
| `music_ed_001` | Air Nomads | `elemental_dreams` | `demo_music/elemental_dreams/air_nomads.ogg` | Flow Music (gerada pelo usuário) | Autorização confirmada pelo responsável; excluída do repo público |
| `music_ed_002` | Water Tribes | `elemental_dreams` | `demo_music/elemental_dreams/water_tribes.ogg` | Flow Music (gerada pelo usuário) | Autorização confirmada pelo responsável; excluída do repo público |

---

## 3. Catálogo Musical Remoto

As demais faixas dos álbuns (quando configurado `R2_BASE_PUBLIC_URL`) são carregadas dinamicamente a partir de um `catalog.json` remoto, também originadas no **Flow Music**, com procedência e autorização confirmadas pelo responsável pelo projeto para a utilização pretendida no aplicativo. O arquivo `catalog.json` de produção não faz parte deste repositório público.
