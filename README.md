# 📱 Compilador Automático de APK no GitHub Actions

Este projeto está totalmente configurado para compilar seu aplicativo Android (`.apk`) de forma automática e segura sempre que você enviar seu código para o GitHub!

---

## 🚀 Como gerar seu APK pelo GitHub (Passo a Passo)

### 1. Crie um repositório no GitHub
1. Acesse seu [GitHub](https://github.com) e crie um novo repositório (pode ser público ou privado).
2. Não adicione README, `.gitignore` ou licença (o projeto já possui tudo isso pronto).

### 2. Envie o seu código para o GitHub
Abra o seu terminal no diretório do projeto e execute os seguintes comandos para subir os arquivos:

```bash
# Inicialize o repositório git
git init

# Adicione todos os arquivos
git add .

# Crie o primeiro commit com a configuração do build
git commit -m "feat: adicionar configuracao de build automatica para o GitHub"

# Defina a branch principal como main
git branch -M main

# Conecte o repositório local ao seu repositório do GitHub (Substitua pela sua URL)
git remote add origin https://github.com/seu-usuario/seu-repositorio.git

# Envie o código para o GitHub
git push -u origin main
```

### 3. Baixe seu APK Prontinho! 🎉
1. Vá até a página do seu repositório no GitHub.
2. Clique na aba **"Actions"** (na barra superior).
3. Você verá o workflow chamado **"Build Android APK"** rodando automaticamente.
4. Quando a compilação terminar (ficar com um ícone verde `check`), clique na execução do workflow.
5. Role a página até a seção **Artifacts** (Artefatos) e clique em **`app-debug-apk`** para baixar o seu arquivo APK compactado!

---

## ⚙️ Detalhes da Configuração de Integração

- **Gradle Wrapper**: Geramos o utilitário `gradlew` no projeto para que o runner do GitHub Actions saiba exatamente qual versão do Gradle usar para compilar de forma perfeita.
- **Assinatura de Depuração Automática**: O workflow localiza e decodifica automaticamente o seu arquivo `debug.keystore.base64` para assinar o app, garantindo que o APK seja 100% instalável em qualquer dispositivo Android de teste.
- **Manutenção de Segredos**: O build configurará automaticamente o arquivo de ambiente (`.env`) baseado no `.env.example` para que os plugins não falhem no ambiente do GitHub.
