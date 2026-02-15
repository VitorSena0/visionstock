# VisionStock - Tutorial Completo de Configuração (Banco + Backend + Mobile)

Este tutorial configura o ambiente completo do VisionStock do zero, incluindo:
- Banco de dados PostgreSQL multi-schema
- Backend Spring Boot (API + JWT)
- App mobile React Native (Expo)

---

## 1. Pré-requisitos

### Sistema
- Linux/macOS/WSL com terminal
- Git

### Banco e Backend
- PostgreSQL 15+
- Java 21
- Maven 3.8+

### Mobile
- Node.js 20.19.5 (recomendado)
- npm
- Expo Go no celular
- Celular e computador na mesma rede Wi-Fi

---

## 2. Clonar o projeto

```bash
git clone <URL_DO_REPOSITORIO>
cd visionstock
```

---

## 3. Configurar Banco de Dados (PostgreSQL)

### 3.1 Criar banco

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE visionstock;
\q
```

### 3.2 Executar migração principal

```bash
psql -U postgres -d visionstock -f database/migrations/001_create_visionstock_schema.sql
```

### 3.3 Validar schema

```bash
sudo -u postgres bash database/validate_schema.sh
```

Se preferir, veja também:
- `database/QUICKSTART.md`
- `database/README.md`

---

## 4. Configurar Backend (Spring Boot)

### 4.1 Criar arquivo de ambiente

```bash
cd backend
cp .env.example .env
```

Edite `backend/.env` se necessário:

```bash
DB_URL=jdbc:postgresql://localhost:5432/visionstock
DB_USERNAME=postgres
DB_PASSWORD=postgres

GEMINI_API_KEY=

# Rede/API
SERVER_ADDRESS=0.0.0.0
PORT=8080

# JWT
JWT_SECRET_KEY=troque-esta-chave-em-producao-com-um-valor-forte
JWT_EXPIRATION_MS=86400000
```

### 4.2 Subir backend

```bash
cd backend
mvn spring-boot:run
```

### 4.3 Teste rápido de saúde da API

Em outro terminal:

```bash
curl -i http://127.0.0.1:8080/api/v1/auth/login
```

Resultado esperado:
- `405 Method Not Allowed` (GET não é permitido para login)

---

## 5. Criar usuário de teste na API

### 5.1 Registrar usuário

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste Mobile","email":"teste@visionstock.com","password":"12345678"}'
```

### 5.2 Testar login

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@visionstock.com","password":"12345678"}'
```

Resultado esperado:
- `200 OK`
- resposta JSON com `token`, `role`, `userId`

---

## 6. Configurar Mobile (React Native + Expo)

### 6.1 Entrar na pasta do app mobile

```bash
cd mobile/vision-stock-mobile
```

### 6.2 Usar Node recomendado

```bash
nvm use 20.19.5
node -v
```

### 6.3 Instalar dependências

```bash
npm install
```

### 6.4 Configurar `.env` do mobile

Copie e edite:

```bash
cp .env.example .env
```

No `mobile/vision-stock-mobile/.env`, configure com o IP LAN da sua máquina:

```bash
EXPO_PUBLIC_API_URL=http://SEU_IP_LOCAL:8080
```

Exemplo real:

```bash
EXPO_PUBLIC_API_URL=http://192.168.100.22:8080
```

Observações:
- Para celular físico: use IP da rede local (`192.168.x.x` ou `10.x.x.x`)
- Para Android Emulator: use `http://10.0.2.2:8080`

### 6.5 Rodar Expo com cache limpo

```bash
npm run start -- --clear
```

Abra o QR Code no Expo Go.

---

## 7. Teste end-to-end (mobile)

1. Abra a tela de login no app.
2. Confirme no rodapé a URL exibida: `API: http://SEU_IP:8080`.
3. Faça login com o usuário criado.
4. Em sucesso, o app deve navegar para a aba inicial autenticada.

---

## 8. Troubleshooting rápido

### Erro no app: “Não foi possível conectar na API”

Verifique:
1. Backend está rodando na porta `8080`
2. `EXPO_PUBLIC_API_URL` está com o IP correto da máquina
3. Celular e computador estão na mesma rede Wi-Fi
4. Firewall do sistema não está bloqueando a porta `8080`

### Login retorna `401 Invalid credentials`

- API está acessível, mas email/senha não conferem
- Refaça o `POST /api/v1/auth/register` e depois `POST /api/v1/auth/login`

### Layout do app sem estilos (UI “crua”)

Rode novamente com cache limpo:

```bash
npm run start -- --clear
```

E confirme que o app está sendo iniciado em `mobile/vision-stock-mobile`.

### Teste com curl em `GET /api/v1/auth/login` retorna erro

Isso é esperado: login é `POST`.

---

## 9. Checklist final

- [ ] Banco `visionstock` criado
- [ ] Migração SQL executada
- [ ] Backend rodando em `0.0.0.0:8080`
- [ ] `POST /api/v1/auth/login` funcionando via curl
- [ ] `.env` do mobile com IP correto
- [ ] Expo rodando com `--clear`
- [ ] Login funcionando no celular

---

## 10. Arquivos de referência

- Banco: `database/migrations/001_create_visionstock_schema.sql`
- Backend config: `backend/src/main/resources/application.properties`
- Mobile API: `mobile/vision-stock-mobile/src/services/api.ts`
- Mobile auth store: `mobile/vision-stock-mobile/src/store/authStore.ts`
- Mobile login: `mobile/vision-stock-mobile/app/(auth)/login.tsx`
