/* ============================================================
   App.tsx — US Conversations (toeic-app 技術採用版)
   - Material Design 3 tokens (materialTokens + typescale + elevation + motionTokens)
   - Howler.js sound effects (sounds.ts)
   - Component-split architecture (pages/ + components/)
   - Dark / Light mode toggle
============================================================ */
import { useState, useEffect } from 'react';
import conversationsData from './assets/conversations.json';
import type { Conversation, MasteryStatus, TabType } from './types';
import { materialTokens } from './theme/tokens';
import { getAvailableVoices, stopSpeaking } from './utils/speech';
import { playSuccess, playReview, setSoundsEnabled } from './utils/sounds';
import { NavigationBar } from './components/NavigationBar';
import { DashboardPage }  from './pages/DashboardPage';
import { SpeechPage }     from './pages/SpeechPage';
import { PracticePage }   from './pages/PracticePage';
import { ExplorerPage }   from './pages/ExplorerPage';
import { SettingsPage }   from './pages/SettingsPage';
import { LoginPage }      from './pages/LoginPage';
import { supabase }       from './utils/supabase';
import type { Session }   from '@supabase/supabase-js';

export default function App() {
  /* ---- Theme ---- */
  const [darkMode, setDarkMode] = useState(true);
  const tokens = darkMode ? materialTokens.dark : materialTokens.light;

  /* ---- Navigation ---- */
  const [activeTab, setActiveTab] = useState<TabType>('dashboard');

  /* ---- Data ---- */
  const [conversations] = useState<Conversation[]>(conversationsData as Conversation[]);

  /* ---- Auth & Progress (Supabase) ---- */
  const [session, setSession] = useState<Session | null>(null);
  const [progress, setProgress] = useState<Record<number, MasteryStatus>>({});
  const [progressLoaded, setProgressLoaded] = useState(false);
  const [authChecked, setAuthChecked] = useState(false);

  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setAuthChecked(true);
    });

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
    });

    return () => subscription.unsubscribe();
  }, []);

  // Fetch progress from DB on login
  useEffect(() => {
    if (!session?.user) {
      setProgress({}); // Clear progress on logout
      setProgressLoaded(false);
      return;
    }

    const fetchProgress = async () => {
      const { data, error } = await supabase
        .from('user_progress')
        .select('card_id, status')
        .eq('user_id', session.user.id);

      if (error) {
        console.error('Error fetching progress:', error);
      } else if (data) {
        const newProgress: Record<number, MasteryStatus> = {};
        data.forEach(row => {
          newProgress[row.card_id] = row.status as MasteryStatus;
        });
        setProgress(newProgress);
      }
      setProgressLoaded(true);
    };

    fetchProgress();
  }, [session]);

  const handleStatusChange = async (id: number, status: MasteryStatus) => {
    // 1. Update UI immediately
    setProgress(prev => ({ ...prev, [id]: status }));

    // 2. Persist to DB
    if (!session?.user) return;
    const { error } = await supabase
      .from('user_progress')
      .upsert({
        user_id: session.user.id,
        card_id: id,
        status: status,
        updated_at: new Date().toISOString()
      }, { onConflict: 'user_id,card_id' });

    if (error) {
      console.error('Error saving progress to Supabase:', error);
    }
  };

  const handleLogout = async () => {
    await supabase.auth.signOut();
  };

  /* ---- Practice category (shared between Dashboard → Practice navigation) ---- */
  const [selectedCategory, setSelectedCategory] = useState('all');

  /* ---- Voice settings (persisted) ---- */
  const [availableVoices, setAvailableVoices]   = useState<SpeechSynthesisVoice[]>([]);
  const [selectedUsVoice, setSelectedUsVoice]   = useState('');
  const [selectedUkVoice, setSelectedUkVoice]   = useState('');
  const [ttsRate, setTtsRate]                   = useState(0.95);

  /* ---- Sound effects toggle ---- */
  const [soundsEnabled, _setSoundsEnabled] = useState(true);
  const handleSetSoundsEnabled = (v: boolean) => {
    _setSoundsEnabled(v);
    setSoundsEnabled(v);
  };

  /* ---- Countdown ---- */
  const [countdownDays, setCountdownDays] = useState(0);

  /* ---- Effects ---- */
  useEffect(() => {
    const loadVoices = () => {
      const voices = getAvailableVoices();
      setAvailableVoices(voices);
      if (voices.length > 0) {
        const us = voices.find(v =>
          v.lang.startsWith('en-US') &&
          (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('David'))
        );
        const uk = voices.find(v =>
          v.lang.startsWith('en-GB') &&
          (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('Hazel') || v.name.includes('Susan'))
        );
        setSelectedUsVoice(prev => prev || us?.name || voices.find(v => v.lang.startsWith('en-US'))?.name || '');
        setSelectedUkVoice(prev => prev || uk?.name || voices.find(v => v.lang.startsWith('en-GB'))?.name || '');
      }
    };
    loadVoices();
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }

    // Countdown to August 14, 2026
    const target = new Date('2026-08-14T00:00:00');
    const diff = Math.ceil((target.getTime() - Date.now()) / 86400000);
    setCountdownDays(Math.max(0, diff));
  }, []);

  // Apply background color to body
  useEffect(() => {
    document.body.style.backgroundColor = materialTokens.dark.surface;
  }, []);

  /* ---- Derived stats ---- */
  const masteredCount    = Object.values(progress).filter(s => s === 'mastered').length;
  const reviewCount      = Object.values(progress).filter(s => s === 'review').length;
  const unattemptedCount = conversations.length - masteredCount - reviewCount;

  /* ---- Tab change handler (stops audio) ---- */
  const handleTabChange = (tab: TabType) => {
    stopSpeaking();
    setActiveTab(tab);
  };

  /* ---- Navigate from Dashboard to Practice with a category ---- */
  const handleNavigateToPractice = (categoryId: string) => {
    setSelectedCategory(categoryId);
    setActiveTab('practice');
  };

  // If still checking auth, show nothing or a spinner
  if (!authChecked) {
    return null; 
  }

  // If not logged in, show Login Page
  if (!session) {
    return <LoginPage tokens={tokens} />;
  }

  return (
    <div style={{
      maxWidth: '1280px',
      margin: '0 auto',
      padding: '2.5rem 1.5rem',
      minHeight: '100vh',
    }}>
      {/* Navigation header + tabs */}
      <NavigationBar
        activeTab={activeTab}
        onTabChange={handleTabChange}
        tokens={tokens}
        masteredCount={masteredCount}
        reviewCount={reviewCount}
        unattemptedCount={unattemptedCount}
        countdownDays={countdownDays}
        darkMode={darkMode}
        setDarkMode={setDarkMode}
        onLogout={handleLogout}
      />

      {/* Page content */}
      <main>
        {activeTab === 'dashboard' && (
          <DashboardPage
            conversations={conversations}
            progress={progress}
            tokens={tokens}
            onNavigateToPractice={handleNavigateToPractice}
          />
        )}

        {activeTab === 'speech' && (
          <SpeechPage tokens={tokens} />
        )}

        {activeTab === 'practice' && (
          <PracticePage
            conversations={conversations}
            progress={progress}
            progressLoaded={progressLoaded}
            onStatusChange={handleStatusChange}
            tokens={tokens}
            selectedCategory={selectedCategory}
            setSelectedCategory={setSelectedCategory}
            onPlaySuccess={playSuccess}
            onPlayReview={playReview}
          />
        )}

        {activeTab === 'explorer' && (
          <ExplorerPage
            conversations={conversations}
            progress={progress}
            onStatusChange={handleStatusChange}
            tokens={tokens}
            selectedUsVoice={selectedUsVoice}
            selectedUkVoice={selectedUkVoice}
            ttsRate={ttsRate}
          />
        )}

        {activeTab === 'settings' && (
          <SettingsPage
            tokens={tokens}
            availableVoices={availableVoices}
            selectedUsVoice={selectedUsVoice}
            setSelectedUsVoice={setSelectedUsVoice}
            selectedUkVoice={selectedUkVoice}
            setSelectedUkVoice={setSelectedUkVoice}
            ttsRate={ttsRate}
            setTtsRate={setTtsRate}
            progress={progress}
            setProgress={setProgress}
            soundsEnabled={soundsEnabled}
            setSoundsEnabled={handleSetSoundsEnabled}
          />
        )}
      </main>
    </div>
  );
}
