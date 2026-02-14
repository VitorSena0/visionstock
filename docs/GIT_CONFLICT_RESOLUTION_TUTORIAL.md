# 🔧 Tutorial: Resolução de Conflitos de Git - Rebase Travado

## 📋 Visão Geral

Este tutorial documenta como resolver um caso específico de **rebase travado com conflitos add/add** que ocorreu durante a sincronização de branches divergentes. O cenário é comum quando múltiplos desenvolvedores trabalham em paralelo ou quando há cherry-pick/merge manual entre branches.

---

## 🚨 Situação Inicial

### O Problema
```bash
git pull --rebase origin develop
```

Resultou em:
- ✗ Rebase interrompo com conflitos
- ✗ Arquivos em estado add/add (conflito de ambos locais e remote terem versões diferentes)
- ✗ Terminal travado em modo interativo (editor vi/vim preso)
- ✗ Arquivo swap (`COMMIT_EDITMSG.swp`) bloqueando operações

### Arquivos Afetados
```
backend/src/main/java/com/visionstock/controller/ProductController.java
backend/src/main/java/com/visionstock/service/ProductService.java
```

---

## 🔍 Diagnóstico

### Passo 1: Entender o Estado do Rebase

Quando um rebase fica preso, git cria uma estrutura em `.git/rebase-merge/`:

```bash
# Verificar estrutura de rebase
ls -la .git/rebase-merge/
```

**Arquivos-chave:**
- `git-rebase-todo` - Lista de commits a aplicar
- `msgnum` - Qual commit está sendo processado
- `stopped-sha` - Hash do commit que parou
- `rebase-merge/` - Pasta indicando rebase ativo

### Passo 2: Localizar Problema de Travamento

```bash
# Procurar por arquivos swap de editor
ls -la .git/ | grep -E "\.swp|COMMIT_EDIT"
```

Risco: Arquivo `COMMIT_EDITMSG.swp` indica que vim/vi deixou uma sessão de edição aberta.

---

## ✅ Solução Passo a Passo

### Método 1: Abortar Rebase (Quando é muito complicado)

Se o rebase está muito confuso, a abordagem mais simples é:

**1. Abortar rebase:**
```bash
git rebase --abort
```

**2. Fazer merge ao invés de rebase:**
```bash
git merge origin/develop --no-ff -m "Merge message here"
```

**Por que isso funciona melhor?**
- Rebase reescreve histórico (mais limpo, mas exigente)
- Merge preserva histórico (mais seguro, especialmente em equipe)

---

### Método 2: Resolver Conflitos no Merge (O que usamos)

#### Etapa 1️⃣: Limpar Travamento
```bash
# Remover arquivo swap do editor
rm -f .git/COMMIT_EDITMSG.swp

# Abortar rebase
rm -rf .git/rebase-merge .git/rebase-apply 2>/dev/null
git reset --hard HEAD

# Voltar a estado limpo
git status  # Deve mostrar "working tree clean"
```

#### Etapa 2️⃣: Iniciar Merge
```bash
git merge origin/develop --no-ff
```

**O Git dirá:**
```
CONFLITO (adicionar/adicionar): conflito de mesclagem em ProductController.java
CONFLITO (adicionar/adicionar): conflito de mesclagem em ProductService.java
Automatic merge failed; fix conflicts and then commit the result.
```

#### Etapa 3️⃣: Escolher Qual Versão Manter

**Opção A: Manter versão local (--ours)**
```bash
# Se sua versão local é a correta (tem o código novo completo)
git checkout --ours backend/src/main/java/com/visionstock/controller/ProductController.java
git checkout --ours backend/src/main/java/com/visionstock/service/ProductService.java
```

**Opção B: Manter versão remote (--theirs)**
```bash
# Se a versão do remote é a que você quer
git checkout --theirs ProductController.java
git checkout --theirs ProductService.java
```

**Opção C: Resolver manualmente**
```bash
# Abrir arquivo e remover marcadores de conflito:
# <<<<<<< HEAD
# ... sua versão ...
# =======
# ... versão remota ...
# >>>>>>> origin/develop

# Depois editar para manter o código correto
code ProductController.java
```

#### Etapa 4️⃣: Adicionar Arquivos Resolvidos
```bash
git add .
# ou
git add backend/src/main/java/com/visionstock/controller/ProductController.java
git add backend/src/main/java/com/visionstock/service/ProductService.java
```

#### Etapa 5️⃣: Commit do Merge
```bash
git commit -m "Merge origin/develop com Approval Workflow implementation"
```

---

## 🔄 Sincronização Final

### Verificar Status
```bash
git log --oneline -5
git status
```

Você deve ver:
```
Seu ramo está à frente de 'origin/develop' por X submissões.
  (use "git push" to publish your local commits)
```

### Fazer Push
```bash
git push origin develop
```

---

## 🧪 Validação

### Compilação
```bash
cd backend
mvn clean compile -DskipTests
```

Esperado: `BUILD SUCCESS`

### Verificar Código
```bash
# Ver commits sincronizados
git log --oneline origin/develop..HEAD

# Verificar que arquivos estão corretos
git show HEAD:backend/src/main/java/com/visionstock/controller/ProductController.java | head -50
```

---

## 📚 Referência: Comandos Git Úteis

| Comando | Quando usar | Efeito |
|---------|-----------|--------|
| `git rebase --abort` | Rebase muito confuso | Cancela rebase, volta ao estado anterior |
| `git merge --abort` | Merge muito confuso | Cancela merge, volta ao estado anterior |
| `git checkout --ours <file>` | Quer manter local | Usa versão local no arquivo |
| `git checkout --theirs <file>` | Quer manter remote | Usa versão remota no arquivo |
| `git diff --name-only --diff-filter=U` | Listar conflitos | Mostra quais arquivos têm conflitos não resolvidos |
| `git reset --hard HEAD` | Estado confuso | Volta ao último commit, descarta mudanças |
| `git reset --hard REBASE_HEAD` | Durante rebase broken | Volta ao que era antes do rebase |

---

## 🎯 Quando Usar Cada Estratégia

### ✅ Use REBASE quando:
- Está trabalhando sozinho em um feature branch
- Quer histórico linear e limpo
- Não há colaboradores no branch
- Exemplo: `feature/approval-workflow`

### ✅ Use MERGE quando:
- Há múltiplos desenvolvedores
- Branches foram divergindo
- Quer preservar histórico completo
- Trabalhando em branch `develop` ou `main`
- Exemplo: `develop <- origin/develop`

---

## 🚀 Scenario Real: O Que Aconteceu Aqui

```
Timeline:

1. Local tinha 2 commits com Approval Workflow
2. origin/develop tinha 4 commits diferentes
3. Tentou git pull --rebase (combina fetch + rebase)
4. Rebase conflitou em 2 arquivos
5. Editor vi/vim travou durante merge message
6. Terminal ficou em estado interativo inativo

Solução:
7. Limpou arquivo swap do editor
8. Abortou rebase
9. Fez merge ao invés (estratégia mais segura)
10. Resolveu conflitos mantendo versão local (--ours)
11. Fez commit do merge
12. Push para sincronizar remote
13. Compilação validou tudo OK
```

---

## 💡 Dicas Pro

### 1️⃣ Evitar Problemas
```bash
# Sempre fazer fetch antes de rebase/merge
git fetch origin

# Usar --no-edit para evitar editor travado
git merge origin/develop --no-edit

# Configurar editor padrão
git config --global core.editor "nano"  # ao invés de vi
```

### 2️⃣ Resolver Conflitos Automaticamente (Com Cuidado!)
```bash
# Manter sempre local no merge
git merge -X ours origin/develop

# Manter sempre remote
git merge -X theirs origin/develop
```

### 3️⃣ Verificar Antes de Fazer Push
```bash
# Ver o que vai subir
git log origin/develop..HEAD

# Ver diffs dos commits que faltam no remote
git diff origin/develop...HEAD
```

---

## 🔗 Recursos Adicionais

- [Git Documentation: Merge](https://git-scm.com/docs/git-merge)
- [Git Documentation: Rebase](https://git-scm.com/docs/git-rebase)
- [Atlassian Tutorial: Merging vs Rebasing](https://www.atlassian.com/git/tutorials/merging-vs-rebasing)

---

## ❓ FAQ

### P: Perdi meus commits durante o merge?
**R:** Não! Use `git reflog` para encontrar commit lost:
```bash
git reflog  # Lista todos os estados anterior
git reset --hard <commit-hash>  # Voltar se necessário
```

### P: Como desfazer um merge já feito?
**R:** 
```bash
git reset --hard HEAD~1  # Volta 1 commit atrás
# ou
git revert -m 1 <merge-commit-hash>  # Cria novo commit desfazendo
```

### P: Merge criou commits extras que não quero?
**R:**
```bash
# Usar rebase interativo para limpar
git rebase -i origin/develop
# Marcar commits como 'squash' ou 'drop'
```

---

## ✨ Conclusão

Conflitos de git são **normais e solucionáveis**. A chave é:
1. ✅ Entender qual estratégia usar (merge vs rebase)
2. ✅ Sempre fazer backup mental (use `git reflog`)
3. ✅ Limpar travamentos antes de tentar resolver
4. ✅ Testar compilação após resolver
5. ✅ Fazer push quando tudo estiver OK

**Lembra:** Quando em dúvida, `git merge` é mais seguro que `git rebase`! 🚀
