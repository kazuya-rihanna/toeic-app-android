import React from 'react';
import type { TabType } from '../types';
import { typescale, motionTokens, elevation } from '../theme/tokens';

interface NavigationBarProps {
  activeTab: TabType;
  onTabChange: (tab: TabType) => void;
  tokens: Record<string, string>;
  masteredCount: number;
  reviewCount: number;
  unattemptedCount: number;
  countdownDays: number;
  darkMode: boolean;
  setDarkMode: (v: boolean) => void;
  onLogout: () => void;
}

const TABS: { id: TabType; label: string; emoji: string }[] = [
  { id: 'dashboard', label: 'Dashboard',             emoji: '🏠' },
  { id: 'speech',    label: 'Self-Introduction',     emoji: '🎤' },
  { id: 'practice',  label: 'Q&A Practice',          emoji: '💬' },
  { id: 'explorer',  label: '200 List Explorer',     emoji: '📋' },
  { id: 'settings',  label: 'Settings',              emoji: '⚙️' },
];

export const NavigationBar: React.FC<NavigationBarProps> = ({
  activeTab, onTabChange, tokens,
  masteredCount, reviewCount, unattemptedCount, countdownDays,
  darkMode, setDarkMode, onLogout,
}) => {
  const headerStyle: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: `1px solid ${tokens['outline-variant']}`,
    ...elevation.level2,
    borderRadius: '20px',
    padding: '1.5rem 2rem',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '1.5rem',
    flexWrap: 'wrap' as const,
    gap: '1rem',
  };

  const tabStyle = (isActive: boolean): React.CSSProperties => ({
    padding: '0.65rem 1.15rem',
    borderRadius: '12px',
    fontWeight: 600,
    fontSize: '0.88rem',
    cursor: 'pointer',
    border: `1px solid ${isActive ? tokens['primary'] : tokens['outline-variant']}`,
    background: isActive
      ? tokens['primary-container']
      : tokens['surface-container'],
    color: isActive ? tokens['on-primary-container'] : tokens['on-surface-variant'],
    backdropFilter: 'blur(8px)',
    WebkitBackdropFilter: 'blur(8px)',
    transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
    fontFamily: 'inherit',
    outline: 'none',
    boxShadow: isActive ? `0 0 14px ${tokens['primary-container']}` : 'none',
    ...typescale['label-large'],
  });

  const statsChipStyle: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(8px)',
    border: `1px solid ${tokens['outline-variant']}`,
    padding: '0.45rem 1rem',
    borderRadius: '12px',
    display: 'flex',
    gap: '1.25rem',
  };

  return (
    <div>
      {/* ---- App Header ---- */}
      <header style={headerStyle}>
        <div>
          <h1 style={{
            ...typescale['headline-small'],
            fontWeight: 800,
            background: `linear-gradient(135deg, ${tokens['on-surface']} 30%, ${tokens['primary']} 100%)`,
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            display: 'flex',
            alignItems: 'center',
            gap: '0.65rem',
            margin: 0,
          }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none"
              stroke={tokens['primary']} strokeWidth="2.5"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            US Prep Conversational App
          </h1>
          <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'], marginTop: '0.3rem' }}>
            Practice conversational English for the upcoming visit on August 14th
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.85rem', alignItems: 'center', flexWrap: 'wrap' as const }}>
          {/* Countdown */}
          <div style={{ textAlign: 'right' }}>
            <span style={{ ...typescale['label-small'], color: tokens['on-surface-variant'], display: 'block', textTransform: 'uppercase', letterSpacing: '1px' }}>
              Countdown
            </span>
            <span style={{ fontWeight: 800, fontSize: '1.7rem', color: tokens['primary'], lineHeight: 1.1 }}>
              {countdownDays}{' '}
              <span style={{ ...typescale['body-medium'], color: tokens['on-surface'] }}>Days</span>
            </span>
          </div>

          {/* Stats chip */}
          <div style={statsChipStyle}>
            <div style={{ textAlign: 'center' }}>
              <span style={{ ...typescale['label-small'], color: tokens['on-surface-variant'], display: 'block' }}>Mastered</span>
              <span style={{ fontWeight: 700, color: tokens['primary'] }}>{masteredCount}</span>
            </div>
            <div style={{ textAlign: 'center' }}>
              <span style={{ ...typescale['label-small'], color: tokens['on-surface-variant'], display: 'block' }}>Review</span>
              <span style={{ fontWeight: 700, color: '#FBBF24' }}>{reviewCount}</span>
            </div>
            <div style={{ textAlign: 'center' }}>
              <span style={{ ...typescale['label-small'], color: tokens['on-surface-variant'], display: 'block' }}>Left</span>
              <span style={{ fontWeight: 700, color: tokens['on-surface-variant'] }}>{unattemptedCount}</span>
            </div>
          </div>

          {/* Dark / Light toggle and Logout */}
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <button
              onClick={() => setDarkMode(!darkMode)}
              style={{
                background: tokens['surface-container-high'], border: `1px solid ${tokens['outline-variant']}`,
                borderRadius: '50%', width: '38px', height: '38px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer', ...typescale['body-large'], color: tokens['on-surface'],
                transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
              }}
              title={darkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            >
              {darkMode ? '☀️' : '🌙'}
            </button>
            <button
              onClick={onLogout}
              style={{
                background: 'transparent', border: `1px solid ${tokens['outline-variant']}`,
                borderRadius: '12px', padding: '0.4rem 0.8rem',
                cursor: 'pointer', ...typescale['label-medium'], color: tokens['on-surface-variant'],
                transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
              }}
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* ---- Tab Navigation ---- */}
      <nav style={{ display: 'flex', gap: '0.6rem', marginBottom: '2rem', flexWrap: 'wrap' as const }}>
        {TABS.map(tab => (
          <button
            key={tab.id}
            style={tabStyle(activeTab === tab.id)}
            onClick={() => onTabChange(tab.id)}
          >
            <span style={{ marginRight: '0.35rem' }}>{tab.emoji}</span>
            {tab.label}
          </button>
        ))}
      </nav>
    </div>
  );
};

export default NavigationBar;
