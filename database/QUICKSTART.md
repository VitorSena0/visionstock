# VisionStock - Quick Start Guide

## 🚀 Início Rápido

Este guia ajudará você a configurar o banco de dados do VisionStock em menos de 5 minutos.

## Pré-requisitos

- PostgreSQL 12 ou superior instalado
- Acesso de administrador ao PostgreSQL
- Cliente PostgreSQL (`psql`) configurado

## Instalação em 3 Passos

### 1️⃣ Criar o Banco de Dados

```bash
# Conectar ao PostgreSQL
sudo -u postgres psql

# Criar o banco
CREATE DATABASE visionstock;

# Sair
\q
```

### 2️⃣ Executar o Script DDL

```bash
# Navegar até o diretório do projeto
cd /caminho/para/visionstock

# Executar o script de migração
psql -U postgres -d visionstock -f database/migrations/001_create_visionstock_schema.sql
```

**Saída esperada:**
```
CREATE EXTENSION
CREATE SCHEMA
CREATE TABLE
...
INSERT 0 2  (roles)
INSERT 0 6  (categories)
```

### 3️⃣ Verificar a Instalação

```bash
# Executar script de validação
sudo -u postgres bash database/validate_schema.sh
```

Ou manualmente:

```sql
-- Conectar ao banco
psql -U postgres -d visionstock

-- Listar schemas criados
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name IN ('auth', 'inventory', 'finance', 'system');

-- Deve retornar: auth, inventory, finance, system
```

## 🎯 Próximos Passos

### Criar Primeiro Usuário

```sql
-- Conectar ao banco
psql -U postgres -d visionstock

-- Criar usuário administrador
INSERT INTO auth.users (nome, email, senha_hash, role, ativo)
VALUES (
    'Admin Sistema',
    'admin@visionstock.com',
    '$2b$10$HASH_AQUI',  -- Substituir pelo hash bcrypt real
    'ADMIN',
    true
);
```

### Explorar Dados de Exemplo

O banco já vem com:
- ✅ 2 roles padrão (ADMIN, USER)
- ✅ 6 categorias de produtos pré-cadastradas

Para ver:
```sql
SELECT * FROM auth.roles;
SELECT * FROM inventory.categories;
```

### Testar com Dados de Exemplo

Execute o arquivo de exemplos:
```bash
# Copie e execute queries do arquivo examples.sql
psql -U postgres -d visionstock -f database/examples.sql
```

## 📚 Documentação Completa

- **Documentação Completa:** `database/README.md`
- **Exemplos de Uso:** `database/examples.sql`
- **Script DDL:** `database/migrations/001_create_visionstock_schema.sql`

## 🔧 Configuração para Desenvolvimento

### Criar Usuário de Aplicação

Para usar em ambiente de desenvolvimento, crie um usuário específico:

```sql
-- Como postgres
CREATE USER visionstock_app WITH PASSWORD 'sua_senha_segura';

-- Conceder permissões
GRANT USAGE ON SCHEMA auth, inventory, finance, system TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA auth TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA inventory TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA finance TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA system TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA inventory TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA finance TO visionstock_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA system TO visionstock_app;
```

### String de Conexão

Use esta string de conexão na sua aplicação:

```
postgresql://visionstock_app:sua_senha_segura@localhost:5432/visionstock
```

**Node.js / TypeScript:**
```javascript
const pool = new Pool({
  host: 'localhost',
  port: 5432,
  database: 'visionstock',
  user: 'visionstock_app',
  password: 'sua_senha_segura',
});
```

**Java / Spring Boot:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/visionstock
spring.datasource.username=visionstock_app
spring.datasource.password=sua_senha_segura
```

**Python / Django:**
```python
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql',
        'NAME': 'visionstock',
        'USER': 'visionstock_app',
        'PASSWORD': 'sua_senha_segura',
        'HOST': 'localhost',
        'PORT': '5432',
    }
}
```

## 🔍 Verificação de Saúde

Execute estas queries para verificar se tudo está funcionando:

```sql
-- Contar tabelas por schema
SELECT 
    table_schema, 
    COUNT(*) as table_count
FROM information_schema.tables 
WHERE table_schema IN ('auth', 'inventory', 'finance', 'system')
    AND table_type = 'BASE TABLE'
GROUP BY table_schema
ORDER BY table_schema;

-- Resultado esperado:
-- auth: 2 tabelas
-- inventory: 3 tabelas
-- finance: 1 tabela
-- system: 1 tabela

-- Verificar views
SELECT table_schema, table_name 
FROM information_schema.views 
WHERE table_schema IN ('finance', 'inventory')
ORDER BY table_schema, table_name;

-- Resultado esperado: 4 views

-- Testar view de lucratividade
SELECT COUNT(*) FROM finance.vw_dashboard_lucratividade;
```

## ❗ Troubleshooting

### Erro: "extensão uuid-ossp não encontrada"

```sql
-- Como usuário postgres
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

### Erro: "permissão negada para schema"

```sql
-- Conceder permissões ao seu usuário
GRANT USAGE ON SCHEMA auth, inventory, finance, system TO seu_usuario;
```

### Banco já existe mas quer recriar

```sql
-- ATENÇÃO: Isso apaga todos os dados!
DROP DATABASE IF EXISTS visionstock;
CREATE DATABASE visionstock;
```

### Resetar dados mantendo estrutura

```sql
-- Truncar todas as tabelas (mantém estrutura)
TRUNCATE TABLE finance.stock_movements CASCADE;
TRUNCATE TABLE inventory.validation_queue CASCADE;
TRUNCATE TABLE inventory.products CASCADE;
TRUNCATE TABLE inventory.categories CASCADE;
TRUNCATE TABLE auth.users CASCADE;
TRUNCATE TABLE auth.roles CASCADE;
TRUNCATE TABLE system.audit_logs CASCADE;

-- Re-inserir dados iniciais
INSERT INTO auth.roles (name, description, permissions) VALUES
('ADMIN', 'Gerente com acesso total', '...'),
('USER', 'Estoquista limitado', '...')
ON CONFLICT DO NOTHING;
```

## 🎓 Aprendendo Mais

### Consultas Úteis para Iniciantes

```sql
-- Ver estrutura de uma tabela
\d inventory.products

-- Ver todos os índices
\di inventory.*

-- Ver triggers
SELECT trigger_name, event_manipulation 
FROM information_schema.triggers 
WHERE trigger_schema = 'inventory';

-- Explicar query (performance)
EXPLAIN ANALYZE 
SELECT * FROM finance.vw_dashboard_lucratividade;
```

### Backup e Restore

```bash
# Fazer backup completo
pg_dump -U postgres -d visionstock -F c -f backup_visionstock.dump

# Restaurar backup
pg_restore -U postgres -d visionstock_novo -v backup_visionstock.dump

# Backup apenas do schema (sem dados)
pg_dump -U postgres -d visionstock --schema-only -f schema_only.sql

# Backup apenas dos dados
pg_dump -U postgres -d visionstock --data-only -f data_only.sql
```

## 🌐 Deploy em Produção

### Opções Recomendadas

1. **Neon** (https://neon.tech)
   - PostgreSQL serverless
   - Free tier generoso
   - Ideal para MVPs

2. **Supabase** (https://supabase.com)
   - PostgreSQL + API REST automática
   - Real-time subscriptions
   - Auth integrada

3. **Railway** (https://railway.app)
   - Deploy fácil
   - PostgreSQL gerenciado
   - CI/CD integrado

4. **AWS RDS** (Produção)
   - Alta disponibilidade
   - Backups automáticos
   - Escalável

### Checklist de Produção

- [ ] Alterar senhas padrão
- [ ] Configurar SSL/TLS
- [ ] Habilitar backups automáticos
- [ ] Configurar retenção de logs
- [ ] Implementar monitoring (pg_stat_statements)
- [ ] Revisar permissões de usuários
- [ ] Configurar connection pooling (PgBouncer)
- [ ] Testar disaster recovery

## 📞 Suporte

- **Documentação:** `database/README.md`
- **Exemplos:** `database/examples.sql`
- **Issues:** GitHub Issues do projeto

---

**VisionStock** - Sistema de Gerenciamento de Estoque Inteligente 🎯
