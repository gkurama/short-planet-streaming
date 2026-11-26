import { useState } from "react";
import { isSupabaseConfigured } from "./lib/supabase";
import {
  Activity, Clapperboard, CloudUpload, Compass, Film, FolderOpen,
  Gauge, Menu, Play, Radio, Scissors, Settings, Sparkles, TrendingUp,
  Upload, WandSparkles, Zap
} from "lucide-react";

type Section = "dashboard" | "lives" | "editor" | "trends" | "style" | "publishing" | "settings";

const navigation: { id: Section; label: string; icon: typeof Gauge }[] = [
  { id: "dashboard", label: "Painel", icon: Gauge },
  { id: "lives", label: "Vídeos e lives", icon: Radio },
  { id: "editor", label: "Editor de clips", icon: Scissors },
  { id: "trends", label: "Tendências", icon: TrendingUp },
  { id: "style", label: "Meu estilo", icon: WandSparkles },
  { id: "publishing", label: "Publicação", icon: Youtube },
  { id: "settings", label: "Definições", icon: Settings },
];

function App() {
  const [active, setActive] = useState<Section>("dashboard");
  const [autoPilot, setAutoPilot] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const title = navigation.find((item) => item.id === active)?.label ?? "Painel";

  return (
    <div className="app-shell">
      <aside className={`sidebar ${menuOpen ? "sidebar-open" : ""}`}>
        <div className="brand">
          <div className="brand-mark"><Clapperboard size={21} /></div>
          <div><strong>PLANET</strong><span>STREAMING</span></div>
        </div>
        <div className="workspace-label">WORKSPACE</div>
        <nav>
          {navigation.map(({ id, label, icon: Icon }) => (
            <button key={id} className={`nav-item ${active === id ? "active" : ""}`} onClick={() => { setActive(id); setMenuOpen(false); }}>
              <Icon size={18} /><span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="storage-card">
            <div className="storage-icon"><CloudUpload size={17} /></div>
            <strong>O teu espaço criativo</strong>
            <p>Os teus projetos e clips organizados num só lugar.</p>
          </div>
          <div className="profile"><div className="avatar">P</div><div><strong>Creator Studio</strong><span>Plano pessoal</span></div><Settings size={16} /></div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button className="mobile-menu icon-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="Abrir menu"><Menu /></button>
          <div className="breadcrumb">Workspace <span>/</span> <strong>{title}</strong></div>
          <div className="top-actions"><span className="status-dot" /> <span className="status-text">Sistema pronto para configurar</span><button className="icon-button"><Activity size={18} /></button></div>
        </header>

        <div className="content">
          <section className="welcome-row">
            <div><div className="eyebrow"><Sparkles size={14} /> O TEU ESTÚDIO CRIATIVO COM IA</div><h1>{active === "dashboard" ? "Transforma momentos em conteúdo." : title}</h1><p>Cria clips verticais dos teus vídeos e transmissões, com um fluxo de trabalho assistido por inteligência artificial.</p></div>
            <button className="primary-button" onClick={() => setActive("lives")}><Upload size={17} /> Adicionar vídeo</button>
          </section>

          {active === "dashboard" && <>
            <section className="hero-card">
              <div className="hero-glow" />
              <div className="hero-copy"><div className="hero-tag"><Zap size={13} /> AUTOPILOTO IA</div><h2>Deixa a IA encontrar<br />os teus melhores momentos.</h2><p>Analisa os teus conteúdos, sugere os melhores excertos e prepara clips prontos para editar.</p><button className={`autopilot-button ${autoPilot ? "running" : ""}`} onClick={() => setAutoPilot(!autoPilot)}><Sparkles size={17} /> {autoPilot ? "Autopiloto ativado (demonstração)" : "Ativar Autopiloto IA"} <span>→</span></button><small>{autoPilot ? "Modo de demonstração ativo. A ligação aos serviços de IA será configurada numa próxima etapa." : "A análise automática ficará disponível após configurar as integrações."}</small></div>
              <div className="hero-art"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="planet"><Play size={36} fill="currentColor" /></div><div className="floating-chip chip-a"><Scissors size={15} /> Clips inteligentes</div><div className="floating-chip chip-b"><Sparkles size={15} /> IA criativa</div><div className="floating-chip chip-c"><Activity size={15} /> 9:16 vertical</div></div>
            </section>

            <div className="section-heading"><div><h3>Centro de criação</h3><p>As ferramentas essenciais para começares.</p></div><span className="muted-label">VISÃO GERAL</span></div>
            <section className="tool-grid">
              <button className="tool-card" onClick={() => setActive("lives")}><div className="tool-icon purple"><Radio /></div><div><strong>Vídeos e lives</strong><p>Importa um link do YouTube e prepara a fonte.</p></div><span>↗</span></button>
              <button className="tool-card" onClick={() => setActive("editor")}><div className="tool-icon blue"><Film /></div><div><strong>Editor de clips</strong><p>Organiza excertos e prepara o formato vertical.</p></div><span>↗</span></button>
              <button className="tool-card" onClick={() => setActive("trends")}><div className="tool-icon orange"><TrendingUp /></div><div><strong>Tendências</strong><p>Área para referências e ideias de conteúdo.</p></div><span>↗</span></button>
              <button className="tool-card" onClick={() => setActive("style")}><div className="tool-icon pink"><WandSparkles /></div><div><strong>Meu estilo</strong><p>Define as tuas preferências de edição.</p></div><span>↗</span></button>
            </section>
            <section className="bottom-grid"><div className="panel"><div className="panel-heading"><div><h3>Começa por aqui</h3><p>Configura o teu fluxo de criação.</p></div><Compass size={19} /></div><div className="step-list"><div><span>01</span><p><strong>Adiciona um vídeo</strong><small>Cola o link de um vídeo ou transmissão.</small></p><button onClick={() => setActive("lives")}>Abrir</button></div><div><span>02</span><p><strong>Escolhe os momentos</strong><small>Prepara os excertos que queres transformar.</small></p><button onClick={() => setActive("editor")}>Abrir</button></div><div><span>03</span><p><strong>Prepara a publicação</strong><small>Revê o formato e os canais de destino.</small></p><button onClick={() => setActive("publishing")}>Abrir</button></div></div></div><div className="panel integration-panel"><div className="panel-heading"><div><h3>Integrações</h3><p>Serviços a configurar.</p></div><Activity size={19} /></div><div className="integration-row"><div className="integration-logo youtube"><Play /></div><div><strong>YouTube</strong><small>Importação e publicação</small></div><span className="pill pending">Por ligar</span></div><div className="integration-row"><div className="integration-logo supabase">S</div><div><strong>Supabase</strong><small>Base de dados e ficheiros</small></div><span className="pill pending">Por configurar</span></div><div className="integration-row"><div className="integration-logo tiktok">♪</div><div><strong>TikTok</strong><small>Publicação de clips</small></div><span className="pill pending">Por ligar</span></div></div></section>
          </>}

          {active === "lives" && <section className="panel page-panel"><div className="panel-heading"><div><h3>Adicionar vídeo ou transmissão</h3><p>Começa por indicar um endereço público do YouTube.</p></div><Radio /></div><div className="dropzone"><div className="drop-icon"><CloudUpload /></div><strong>Importar conteúdo</strong><p>Cola o link do YouTube para preparar a importação.</p><input aria-label="Link do YouTube" placeholder="https://www.youtube.com/watch?v=..." /><button className="primary-button" onClick={() => alert("A importação será ativada quando a API do YouTube estiver configurada.")}>Preparar vídeo <span>→</span></button><small>Protótipo de interface: a importação real será ligada numa próxima etapa.</small></div></section>}

          {active === "editor" && <section className="panel page-panel editor-layout"><div><div className="panel-heading"><div><h3>Editor de clips</h3><p>Pré-visualização do espaço de edição vertical.</p></div><Scissors /></div><div className="preview-stage"><div className="phone-frame"><div className="preview-placeholder"><Play size={30} /><span>PRÉ-VISUALIZAÇÃO 9:16</span></div></div></div></div><div className="editor-side"><h3>Definições do clip</h3><label>Formato<select defaultValue="9:16"><option>9:16</option><option>1:1</option><option>16:9</option></select></label><label>Estilo de legendas<select defaultValue="Dinâmico"><option>Dinâmico</option><option>Minimalista</option><option>Alto contraste</option></select></label><div className="timeline"><span>LINHA DO TEMPO</span><div /></div><p className="helper">As ferramentas de corte, legendagem automática e renderização serão ligadas ao motor de vídeo.</p></div></section>}

          {active === "trends" && <section className="panel page-panel"><div className="panel-heading"><div><h3>Radar de tendências</h3><p>Espaço para acompanhar formatos, temas e referências.</p></div><TrendingUp /></div><div className="empty-state"><div className="empty-icon"><TrendingUp /></div><strong>O radar será ativado em breve</strong><p>A recolha de tendências precisa de fontes e integrações configuradas.</p></div></section>}

          {active === "style" && <section className="panel page-panel"><div className="panel-heading"><div><h3>O meu estilo</h3><p>Preferências que a IA deverá respeitar ao sugerir e editar clips.</p></div><WandSparkles /></div><div className="preferences"><label>Ritmo de edição<select defaultValue="Dinâmico"><option>Dinâmico</option><option>Equilibrado</option><option>Calmo</option></select></label><label>Legendas<select defaultValue="Destacadas"><option>Destacadas</option><option>Discretas</option><option>Sem legendas</option></select></label><label>Enquadramento<select defaultValue="Seguir pessoa"><option>Seguir pessoa</option><option>Centro</option><option>Manual</option></select></label><label>Duração preferida<select defaultValue="30–60 segundos"><option>15–30 segundos</option><option>30–60 segundos</option><option>60–90 segundos</option></select></label></div><p className="helper">Nesta primeira versão, as preferências ficam apenas na interface. A gravação no Supabase será adicionada depois.</p></section>}

          {active === "publishing" && <section className="panel page-panel"><div className="panel-heading"><div><h3>Publicação</h3><p>Prepara os teus clips para os canais de destino.</p></div><Play /></div><div className="publish-targets"><div><div className="integration-logo youtube"><Play /></div><strong>YouTube Shorts</strong><p>Formato vertical e publicação através da API oficial.</p><span className="pill pending">Integração pendente</span></div><div><div className="integration-logo tiktok">♪</div><strong>TikTok</strong><p>Preparação e publicação conforme as permissões da conta.</p><span className="pill pending">Integração pendente</span></div></div></section>}

          {active === "settings" && <section className="panel page-panel"><div className="panel-heading"><div><h3>Definições e ligações</h3><p>Configura os serviços do projeto com segurança.</p></div><Settings /></div><div className="settings-row"><div><strong>Base de dados — Supabase</strong><p>URL do projeto e chave publicável anon através de VITE_SUPABASE_URL e VITE_SUPABASE_ANON_KEY.</p></div><span className={`pill ${isSupabaseConfigured ? "connected" : "pending"}`}>{isSupabaseConfigured ? "Variáveis detetadas" : "Por configurar"}</span></div><div className="settings-row"><div><strong>Google AI</strong><p>Serviço de IA para análise e assistência de edição.</p></div><span className="pill pending">Por configurar</span></div><div className="settings-row"><div><strong>Processamento de vídeo</strong><p>FFmpeg para cortes, legendas e exportação vertical.</p></div><span className="pill pending">Por configurar</span></div><p className="security-note">Nunca introduzas chaves secretas diretamente no código ou num repositório público.</p></section>}
          <footer>PLANET STREAMING <span>•</span> Estúdio de criação assistido por IA <span>•</span> Versão inicial 0.1.0</footer>
        </div>
      </main>
    </div>
  );
}

export default App;
