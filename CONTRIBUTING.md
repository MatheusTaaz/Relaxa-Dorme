# Contribuindo para o Relaxa e Dorme (`CONTRIBUTING.md`)

Obrigado pelo interesse em contribuir com a versão pública do **Relaxa e Dorme**!

---

## 1. Licença das Contribuições

O código-fonte deste repositório é licenciado sob **GNU General Public License v3.0 apenas (`GPL-3.0-only`)**.  
Ao enviar uma contribuição (Pull Request ou patch), você concorda que sua contribuição de código seja distribuída sob os termos da licença [`LICENSE`](LICENSE).

---

## 2. O que NÃO deve ser enviado

Por razões de segurança e licenciamento, **nunca** envie em commits ou Pull Requests:

- Assets proprietários (ilustrações da Ghosty, sons ambientes, músicas demo, capas ou `catalog.json` oficial).
- Chaves de assinatura (`*.jks`, `*.keystore`, `*.pfx`, `*.p12`, `*.pem`, `*.key`).
- Credenciais, tokens de API ou arquivos `.env`.
- O arquivo `local.properties` (específico da sua máquina local e já listado no `.gitignore`).
- URLs privadas ou de produção do Cloudflare R2 hardcoded no código-fonte.

Consulte [`LICENSE-ASSETS.md`](LICENSE-ASSETS.md) e [`AUDIO_LICENSES.md`](AUDIO_LICENSES.md) caso tenha dúvidas sobre recursos visuais e sonoros.

---

## 3. Configuração do Ambiente Local

1. Instale o **Android Studio** e o **Android SDK** (compileSdk 37, minSdk 26).
2. Abra o projeto em `RelaxaeDorme-Public`. O Android Studio criará automaticamente o `local.properties` apontando para o seu SDK local.
3. Por padrão, `BuildConfig.R2_BASE_PUBLIC_URL` fica vazio (`""`). Caso queira testar um catálogo próprio compatível localmente sem alterar o código-fonte, passe a propriedade pelo Gradle ou variável de ambiente:
   - Via propriedade Gradle: `./gradlew assembleDebug -PR2_BASE_PUBLIC_URL=https://seu-bucket.exemplo`
   - Via variável de ambiente: `R2_BASE_PUBLIC_URL=https://seu-bucket.exemplo`

---

## 4. Verificações Obrigatórias Antes de Enviar Mudanças

Execute os comandos abaixo na raiz do projeto antes de abrir um Pull Request:

```bash
# 1. Testes unitários
./gradlew testDebugUnitTest

# 2. Compilação debug
./gradlew assembleDebug

# 3. Verificação estática (Android Lint)
./gradlew lintDebug
```

No Windows (PowerShell/CMD), utilize `.\gradlew.bat`.

---

## 5. Diretrizes de Código

- Mantenha a compatibilidade com o modo comunitário (sem assets locais e sem URL R2 configurada).
- Preserve os fallbacks visuais, estados vazios e suporte aos idiomas **PT-BR**, **EN** e **ES**.
- Não altere dependências ou configurações de build sem necessidade justificada.
- Escreva testes unitários para novas regras de negócio ou correções de bugs.
