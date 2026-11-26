# PLANET STREAMING

Plataforma web e Android para transformar vídeos e transmissões do YouTube em clips verticais curtos, com assistência de IA.

## Objetivo

- Importar vídeos e transmissões do YouTube.
- Identificar momentos relevantes para criar Shorts.
- Editar clips em formato vertical 9:16, com legendas e ajustes de enquadramento.
- Aprender preferências de edição do utilizador.
- Acompanhar tendências e preparar publicações para YouTube Shorts e TikTok.
- Empacotar a aplicação Android com Capacitor.

## Estado do projeto

Este repositório inicia a base da interface. A base inicial do Supabase está preparada: cliente de navegador, modelo SQL e políticas RLS. Ainda é necessário configurar as variáveis do projeto e executar o SQL no Supabase. O processamento de vídeo/FFmpeg, serviços de IA e publicação social serão adicionados por etapas.

## Stack planeada

- React + TypeScript + Vite
- Capacitor para Android
- Supabase para autenticação, dados e armazenamento
- FFmpeg para processamento de vídeo
- APIs oficiais do YouTube e TikTok, sujeitas às permissões e políticas de cada plataforma

## Configurar o Supabase

1. Cria um projeto no Supabase.
2. Copia a URL do projeto e a chave publicável (anon) para as variáveis `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY`. Nunca uses a chave `service_role` no navegador.
3. No SQL Editor do Supabase, executa `supabase/schema.sql`.
4. Cria um ficheiro `.env.local` na raiz do projeto com os dois valores. O ficheiro está excluído do Git.
5. Abre **Definições → Testar ligação** na aplicação. O teste confirma acesso à API e à tabela `projects`; sem sessão iniciada, a política RLS pode devolver zero registos e isso é esperado.

Exemplo:

```env
VITE_SUPABASE_URL=https://<project-id>.supabase.co
VITE_SUPABASE_ANON_KEY=<publishable-or-anon-key>
```

## Executar e compilar

No Google AI Studio, importa o repositório GitHub e mantém o comando de instalação `npm install` e o comando de desenvolvimento `npm run dev`. Para validar uma compilação de produção, usa `npm run build`.

Localmente:

```bash
npm install
npm run dev
```

As integrações de processamento de vídeo, Google AI e publicação social ainda precisam de implementação e das respetivas credenciais/API.

## Segurança

Nunca coloque chaves privadas, tokens, palavras-passe ou ficheiros `.env` reais neste repositório público. Use variáveis de ambiente locais e configure segredos no serviço de deploy.
