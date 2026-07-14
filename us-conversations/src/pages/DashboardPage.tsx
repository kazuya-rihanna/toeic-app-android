import React from 'react';
import type { Conversation, MasteryStatus } from '../types';
import { CATEGORIES } from '../types';
import { typescale, elevation, motionTokens } from '../theme/tokens';

interface DashboardPageProps {
  conversations: Conversation[];
  progress: Record<number, MasteryStatus>;
  tokens: Record<string, string>;
  onNavigateToPractice: (categoryId: string) => void;
}

function getCategoryStats(
  conversations: Conversation[],
  progress: Record<number, MasteryStatus>,
  categoryName: string
) {
  const list = categoryName === 'all'
    ? conversations
    : conversations.filter(item => item.cat === categoryName);
  const total = list.length;
  const mastered = list.filter(item => progress[item.id] === 'mastered').length;
  const review = list.filter(item => progress[item.id] === 'review').length;
  return { total, mastered, review, unattempted: total - mastered - review };
}

const CATEGORY_EMOJIS: Record<string, string> = {
  'Greetings':         '👋',
  'Self-Introduction': '🙋',
  'Work & Career':     '💼',
  'Tech & Apps':       '💻',
  'Career Vision':     '🚀',
  'Saitama Guide':     '🗾',
};

export const DashboardPage: React.FC<DashboardPageProps> = ({
  conversations, progress, tokens, onNavigateToPractice,
}) => {
  const glassCard: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: `1px solid ${tokens['outline-variant']}`,
    borderRadius: '16px',
    ...elevation.level2,
  };

  const totalMastered = Object.values(progress).filter(s => s === 'mastered').length;
  const totalCards = conversations.length;
  const overallPct = totalCards > 0 ? Math.round((totalMastered / totalCards) * 100) : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }} className="animate-fade-in">

      {/* Welcome banner */}
      <div className="glow-active" style={{
        ...glassCard,
        padding: '2rem',
        display: 'flex',
        alignItems: 'center',
        gap: '1.75rem',
      }}>
        <div style={{ fontSize: '3rem', flexShrink: 0 }}>🎯</div>
        <div>
          <h2 style={{ ...typescale['headline-small'], color: tokens['on-surface'], marginBottom: '0.5rem' }}>
            Welcome Prep Session!
          </h2>
          <p style={{ ...typescale['body-medium'], color: tokens['on-surface-variant'], lineHeight: 1.7 }}>
            John (American) and Emily (British) are arriving from Kyoto on{' '}
            <strong style={{ color: tokens['on-surface'] }}>August 14th</strong>.{' '}
            Use the <strong style={{ color: tokens['primary'] }}>Self-Introduction</strong> tab to master your full pitch,
            or dive into <strong style={{ color: tokens['primary'] }}>Q&amp;A Practice</strong> to train all 200 scenarios.
          </p>
          {/* Overall progress bar */}
          <div style={{ marginTop: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', ...typescale['label-small'], color: tokens['on-surface-variant'], marginBottom: '0.35rem' }}>
              <span>Overall Mastery</span>
              <span>{totalMastered}/{totalCards} ({overallPct}%)</span>
            </div>
            <div style={{ height: '6px', background: tokens['outline-variant'], borderRadius: '3px', overflow: 'hidden' }}>
              <div style={{
                width: `${overallPct}%`, height: '100%',
                background: `linear-gradient(90deg, ${tokens['on-primary']}, ${tokens['primary']})`,
                borderRadius: '3px',
                transition: `width ${motionTokens.duration.long1} ${motionTokens.easing.standard}`,
              }} />
            </div>
          </div>
        </div>
      </div>

      {/* Category cards grid */}
      <div>
        <h2 style={{ ...typescale['title-large'], color: tokens['on-surface'], marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={tokens['primary']} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
          </svg>
          Conversational Chapters
        </h2>

        <div className="categories-grid">
          {CATEGORIES.slice(1).map(cat => {
            const stats = getCategoryStats(conversations, progress, cat.id);
            const pct = stats.total > 0 ? Math.round((stats.mastered / stats.total) * 100) : 0;

            return (
              <div
                key={cat.id}
                className="hover-glow"
                style={{
                  ...glassCard,
                  padding: '1.5rem',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '1rem',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                    <span style={{ fontSize: '1.3rem' }}>{CATEGORY_EMOJIS[cat.id] ?? '💬'}</span>
                    <span style={{ ...typescale['label-medium'], color: tokens['primary'], textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                      {cat.label}
                    </span>
                  </div>
                  <h3 style={{ ...typescale['title-medium'], color: tokens['on-surface'], marginBottom: '0.4rem' }}>
                    {cat.jp}
                  </h3>
                  <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'] }}>
                    {stats.total} conversations
                  </p>
                </div>

                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', ...typescale['label-small'], color: tokens['on-surface-variant'], marginBottom: '0.3rem' }}>
                    <span>Mastery</span>
                    <span style={{ color: tokens['on-surface'] }}>{stats.mastered}/{stats.total} ({pct}%)</span>
                  </div>
                  <div style={{ height: '5px', background: tokens['outline-variant'], borderRadius: '3px', overflow: 'hidden', marginBottom: '1rem' }}>
                    <div style={{
                      width: `${pct}%`, height: '100%',
                      background: `linear-gradient(90deg, ${tokens['on-primary']}, ${tokens['primary']})`,
                      borderRadius: '3px',
                    }} />
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem', ...typescale['label-small'], color: tokens['on-surface-variant'], marginBottom: '1rem' }}>
                    <span style={{ color: tokens['primary'] }}>✓ {stats.mastered} mastered</span>
                    <span>·</span>
                    <span style={{ color: '#FBBF24' }}>⚠ {stats.review} review</span>
                    <span>·</span>
                    <span>{stats.unattempted} left</span>
                  </div>
                  <button
                    onClick={() => onNavigateToPractice(cat.id)}
                    style={{
                      width: '100%',
                      padding: '0.5rem',
                      borderRadius: '10px',
                      border: `1px solid ${tokens['outline-variant']}`,
                      background: tokens['surface-container-high'],
                      color: tokens['on-surface'],
                      cursor: 'pointer',
                      ...typescale['label-large'],
                      transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
                    }}
                    onMouseEnter={e => {
                      (e.currentTarget as HTMLButtonElement).style.borderColor = tokens['primary'];
                      (e.currentTarget as HTMLButtonElement).style.color = tokens['primary'];
                    }}
                    onMouseLeave={e => {
                      (e.currentTarget as HTMLButtonElement).style.borderColor = tokens['outline-variant'];
                      (e.currentTarget as HTMLButtonElement).style.color = tokens['on-surface'];
                    }}
                  >
                    Start Practice →
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
