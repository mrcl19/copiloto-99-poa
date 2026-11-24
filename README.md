# Copiloto 99 POA — Projeto Android Nativo (Janela Flutuante)

Aplicativo Android nativo em Kotlin que monitora chamadas na tela da **99 Motorista** via `AccessibilityService` e exibe uma **janela flutuante automática** no topo da tela mostrando:
- **Veredito instantâneo:** `ACEITAR CORRIDA` ou `RECUSAR CORRIDA`
- **R$/km total** (somando busca + viagem)
- **R$/minuto** e **Projeção R$/hora**
- **Travas automáticas do Plano Modo Turbo (05:30 às 19:00):**
  - Bloqueio automático de corridas para Zona Sul e extremos após as 15h (garantindo chegada às 19h na Santa Casa)
  - Trava de segurança para corridas em dinheiro fora da janela (07:00 às 17:30), notas abaixo de 4,85 e contas novas
  - Régua de R$/km ajustada automaticamente por faixa horária

---

## Como gerar o arquivo `.apk` sem precisar instalar nada pesado (GitHub Actions — 3 minutos)

1. Descompacte este arquivo `.zip`.
2. Crie uma conta gratuita no [GitHub](https://github.com) e crie um repositório novo (ex: `copiloto-99-poa`).
3. Envie todos os arquivos descompactados para o repositório.
4. Acesse a aba **Actions** no seu repositório do GitHub, clique em **Compilar APK Copiloto 99 POA** e em **Run workflow**.
5. Assim que terminar (cerca de 2 minutos), baixe o arquivo **`Copiloto99POA-debug-apk`** no final da página, descompacte o `app-debug.apk` e instale no seu Android.

*(Alternativamente, você pode abrir a pasta `Copiloto99POA` diretamente no **Android Studio** e clicar em **Build > Build Bundle(s) / APK(s) > Build APK(s)**).*

---

## Como configurar no celular após instalar o APK

1. Abra o **Copiloto 99 POA**.
2. Toque em **Autorizar Janela Flutuante** e ative a permissão de sobreposição.
3. Toque em **Ativar Leitura da Tela 99** > **Apps instalados** > ative **Copiloto 99 POA — Leitor de Corridas**.
4. Toque em **Testar Janela Flutuante Agora** para ver o card flutuante aparecendo no topo da tela.
5. Toque em **Iniciar Copiloto em Segundo Plano** e abra o aplicativo da 99.
