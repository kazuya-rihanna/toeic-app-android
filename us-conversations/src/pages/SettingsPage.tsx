import React from 'react';
import { typescale, elevation, motionTokens } from '../theme/tokens';
import type { MasteryStatus } from '../types';

interface SettingsPageProps {
  tokens: Record<string, string>;
  availableVoices: SpeechSynthesisVoice[];
  selectedUsVoice: string;
  setSelectedUsVoice: (v: string) => void;
  selectedUkVoice: string;
  setSelectedUkVoice: (v: string) => void;
  ttsRate: number;
  setTtsRate: (r: number) => void;
  progress: Record<number, MasteryStatus>;
  setProgress: (p: Record<number, MasteryStatus>) => void;
  soundsEnabled: boolean;
  setSoundsEnabled: (v: boolean) => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  tokens,
  availableVoices,
  selectedUsVoice, setSelectedUsVoice,
  selectedUkVoice, setSelectedUkVoice,
  ttsRate, setTtsRate,
  progress, setProgress,
  soundsEnabled, setSoundsEnabled,
}) => {
  const glassCard: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: `1px solid ${tokens['outline-variant']}`,
    borderRadius: '16px',
    ...elevation.level2,
    padding: '2rem',
    maxWidth: '600px',
    margin: '0 auto',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block', ...typescale['label-large'],
    color: tokens['on-surface-variant'], marginBottom: '0.45rem',
  };
  const selectStyle: React.CSSProperties = {
    width: '100%', padding: '0.5rem 0.75rem', borderRadius: '8px',
    background: tokens['surface-container-lowest'],
    border: `1px solid ${tokens['outline-variant']}`,
    color: tokens['on-surface'], ...typescale['body-medium'],
    cursor: 'pointer', fontFamily: 'inherit',
  };
  const sectionDivider: React.CSSProperties = {
    borderTop: `1px solid ${tokens['outline-variant']}`,
    paddingTop: '1.5rem', marginTop: '0.5rem',
  };

  const usVoices = availableVoices.filter(v => v.lang.startsWith('en-US'));
  const ukVoices = availableVoices.filter(v => v.lang.startsWith('en-GB'));

  const handleReset = () => {
    if (window.confirm('Reset all progress? This cannot be undone.')) {
      setProgress({});
    }
  };

  const masteredCount = Object.values(progress).filter(s => s === 'mastered').length;
  const reviewCount   = Object.values(progress).filter(s => s === 'review').length;

  return (
    <div className="animate-fade-in" style={glassCard}>
      <h2 style={{ ...typescale['headline-small'], color: tokens['on-surface'], marginBottom: '1.75rem' }}>
        ⚙️ Voice &amp; App Settings
      </h2>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>

        {/* US Voice */}
        <div>
          <label style={labelStyle}>🇺🇸 American Voice (John):</label>
          <select value={selectedUsVoice} onChange={e => setSelectedUsVoice(e.target.value)} style={selectStyle}>
            {usVoices.length > 0
              ? usVoices.map(v => <option key={v.name} value={v.name}>{v.name} ({v.lang})</option>)
              : <option value="">Browser default US English</option>
            }
          </select>
        </div>

        {/* UK Voice */}
        <div>
          <label style={labelStyle}>🇬🇧 British Voice (Emily):</label>
          <select value={selectedUkVoice} onChange={e => setSelectedUkVoice(e.target.value)} style={selectStyle}>
            {ukVoices.length > 0
              ? ukVoices.map(v => <option key={v.name} value={v.name}>{v.name} ({v.lang})</option>)
              : <option value="">Browser default UK English</option>
            }
          </select>
        </div>

        {/* TTS Rate */}
        <div>
          <label style={labelStyle}>
            🔉 Playback Speed (TTS):&nbsp;
            <span style={{ color: tokens['primary'], fontWeight: 700 }}>{ttsRate.toFixed(2)}×</span>
          </label>
          <input
            type="range" min="0.5" max="1.5" step="0.05"
            value={ttsRate}
            onChange={e => setTtsRate(parseFloat(e.target.value))}
            style={{ width: '100%', accentColor: tokens['primary'] }}
          />
          <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'], marginTop: '0.3rem' }}>
            Lower speed helps with pronunciation and spelling. Range: 0.5× (slow) to 1.5× (fast).
          </p>
        </div>

        {/* Sound effects toggle */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <label style={{ ...labelStyle, marginBottom: '0.15rem' }}>🔔 Sound Effects (Howler):</label>
            <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'] }}>
              Plays a chime on "Mastered" and a tone on "Needs Practice".
            </p>
          </div>
          <button
            onClick={() => setSoundsEnabled(!soundsEnabled)}
            style={{
              padding: '0.45rem 1rem', borderRadius: '20px', cursor: 'pointer',
              background: soundsEnabled ? tokens['primary-container'] : tokens['surface-container-high'],
              border: `1px solid ${soundsEnabled ? tokens['primary'] : tokens['outline-variant']}`,
              color: soundsEnabled ? tokens['on-primary-container'] : tokens['on-surface-variant'],
              ...typescale['label-large'],
              transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
              flexShrink: 0, marginLeft: '1rem',
            }}
          >
            {soundsEnabled ? '✓ On' : 'Off'}
          </button>
        </div>

        {/* Progress stats */}
        <div style={sectionDivider}>
          <h3 style={{ ...typescale['title-small'], color: tokens['on-surface'], marginBottom: '0.75rem' }}>
            📊 Current Progress
          </h3>
          <div style={{ display: 'flex', gap: '1.5rem', ...typescale['body-medium'], color: tokens['on-surface-variant'] }}>
            <span>✓ Mastered: <strong style={{ color: tokens['primary'] }}>{masteredCount}</strong></span>
            <span>⚠ Review: <strong style={{ color: '#FBBF24' }}>{reviewCount}</strong></span>
            <span>○ Left: <strong>{Object.keys(progress).length > 0
              ? Math.max(0, Object.keys(progress).length - masteredCount - reviewCount)
              : '?'}</strong>
            </span>
          </div>
        </div>

        {/* Reset */}
        <div style={sectionDivider}>
          <h3 style={{ ...typescale['title-small'], color: tokens['error'], marginBottom: '0.65rem' }}>
            🗑️ Reset Progress
          </h3>
          <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'], marginBottom: '0.85rem' }}>
            Permanently removes all mastered / review records and resets everything to unattempted.
          </p>
          <button
            onClick={handleReset}
            style={{
              padding: '0.55rem 1.25rem', borderRadius: '10px', cursor: 'pointer',
              background: tokens['error-container'],
              border: `1px solid ${tokens['error']}50`,
              color: tokens['error'], ...typescale['label-large'],
              transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
            }}
          >
            Reset All Progress
          </button>
        </div>
      </div>
    </div>
  );
};

export default SettingsPage;
