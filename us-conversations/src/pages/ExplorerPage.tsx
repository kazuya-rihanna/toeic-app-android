import React, { useState, useEffect } from 'react';
import type { Conversation, MasteryStatus } from '../types';
import { CATEGORIES } from '../types';
import { speakText } from '../utils/speech';
import { typescale, elevation, motionTokens } from '../theme/tokens';

interface ExplorerPageProps {
  conversations: Conversation[];
  progress: Record<number, MasteryStatus>;
  onStatusChange: (id: number, status: MasteryStatus) => void;
  tokens: Record<string, string>;
  selectedUsVoice: string;
  selectedUkVoice: string;
  ttsRate: number;
}

const ITEMS_PER_PAGE = 20;

export const ExplorerPage: React.FC<ExplorerPageProps> = ({
  conversations, progress, onStatusChange, tokens,
  selectedUsVoice, selectedUkVoice, ttsRate,
}) => {
  const [search, setSearch]             = useState('');
  const [catFilter, setCatFilter]       = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [currentPage, setCurrentPage]   = useState(1);

  // Reset page on filter change
  useEffect(() => { setCurrentPage(1); }, [search, catFilter, statusFilter]);

  const playTTS = (text: string, voiceType: 'us' | 'uk') => {
    const lang  = voiceType === 'us' ? 'en-US' : 'en-GB';
    const voice = voiceType === 'us' ? selectedUsVoice : selectedUkVoice;
    speakText(text, lang as any, voice, undefined, ttsRate);
  };

  const filtered = conversations.filter(item => {
    const term = search.toLowerCase();
    const searchMatch = !term ||
      item.q.toLowerCase().includes(term) ||
      item.qa.toLowerCase().includes(term) ||
      item.qn.includes(term);
    const status = progress[item.id] || 'unattempted';
    const statusMatch = statusFilter === 'all' || status === statusFilter;
    const catMatch    = catFilter === 'all' || item.cat === catFilter;
    return searchMatch && statusMatch && catMatch;
  });

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE);
  const start = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginated = filtered.slice(start, start + ITEMS_PER_PAGE);

  // ---- Styles ----
  const glassCard: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: `1px solid ${tokens['outline-variant']}`,
    borderRadius: '16px',
    ...elevation.level2,
  };
  const inputStyle: React.CSSProperties = {
    background: tokens['surface-container-lowest'],
    border: `1px solid ${tokens['outline-variant']}`,
    borderRadius: '8px', padding: '0.5rem 0.85rem',
    color: tokens['on-surface'], ...typescale['body-medium'],
    outline: 'none', fontFamily: 'inherit',
  };
  const playMiniBtn: React.CSSProperties = {
    background: tokens['primary-container'],
    border: `1px solid ${tokens['primary']}30`,
    color: tokens['on-primary-container'],
    width: '26px', height: '26px', borderRadius: '6px',
    cursor: 'pointer', display: 'inline-flex',
    alignItems: 'center', justifyContent: 'center',
    fontSize: '0.7rem', flexShrink: 0,
    transition: `all ${motionTokens.duration.short3} ${motionTokens.easing.standard}`,
  };
  const statusColor = (s: MasteryStatus) =>
    s === 'mastered' ? tokens['primary']
    : s === 'review'  ? '#FBBF24'
    : tokens['on-surface-variant'];

  return (
    <div style={{ ...glassCard, padding: '1.5rem' }} className="animate-fade-in">
      <h2 style={{ ...typescale['title-large'], color: tokens['on-surface'], marginBottom: '1.25rem' }}>
        📋 Complete Conversation Library
      </h2>

      {/* Controls */}
      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <input
          type="text"
          className="search-input"
          placeholder="Search English, Japanese…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          style={{ ...inputStyle, flexGrow: 1 }}
        />
        <select value={catFilter} onChange={e => setCatFilter(e.target.value)} style={inputStyle}>
          <option value="all">All Categories</option>
          {CATEGORIES.slice(1).map(cat => (
            <option key={cat.id} value={cat.id}>{cat.label}</option>
          ))}
        </select>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={inputStyle}>
          <option value="all">All Statuses</option>
          <option value="unattempted">Unattempted</option>
          <option value="review">Needs Review</option>
          <option value="mastered">Mastered</option>
        </select>
      </div>

      {/* Result count */}
      <p style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.75rem' }}>
        {filtered.length} entries found
      </p>

      {/* Table */}
      <div style={{ overflowX: 'auto', borderRadius: '8px', border: `1px solid ${tokens['outline-variant']}` }}>
        <table className="explorer-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', ...typescale['body-small'] }}>
          <thead>
            <tr style={{ background: tokens['surface-container-high'], borderBottom: `1px solid ${tokens['outline-variant']}` }}>
              <th style={{ padding: '0.7rem 0.85rem', width: '56px', color: tokens['on-surface-variant'] }}>ID</th>
              <th style={{ padding: '0.7rem 0.85rem', width: '110px', color: tokens['on-surface-variant'] }}>Category</th>
              <th style={{ padding: '0.7rem 0.85rem', color: tokens['on-surface-variant'] }}>Partner Prompt</th>
              <th style={{ padding: '0.7rem 0.85rem', color: tokens['on-surface-variant'] }}>Your Answer / 和訳</th>
              <th style={{ padding: '0.7rem 0.85rem', width: '110px', textAlign: 'center', color: tokens['on-surface-variant'] }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {paginated.map(item => {
              const status = progress[item.id] || 'unattempted';
              return (
                <tr key={item.id} style={{ borderBottom: `1px solid ${tokens['outline-variant']}`, transition: `background ${motionTokens.duration.short3}` }}>
                  <td style={{ padding: '0.85rem', color: tokens['on-surface-variant'] }}>
                    #{String(item.id).padStart(3, '0')}
                  </td>
                  <td style={{ padding: '0.85rem' }}>
                    <span style={{
                      ...typescale['label-small'], padding: '0.18rem 0.45rem',
                      borderRadius: '4px', background: tokens['surface-container-high'],
                      color: tokens['on-surface-variant'],
                    }}>{item.cat}</span>
                  </td>
                  <td style={{ padding: '0.85rem', lineHeight: 1.55 }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.45rem' }}>
                      <button
                        className="play-mini-btn"
                        style={playMiniBtn}
                        onClick={() => playTTS(item.q, 'us')}
                        title="Play (US)"
                      >🔊</button>
                      <span style={{ color: tokens['on-surface'] }}>"{item.q}"</span>
                    </div>
                  </td>
                  <td style={{ padding: '0.85rem', lineHeight: 1.6 }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.45rem', marginBottom: '0.2rem' }}>
                      <button
                        className="play-mini-btn"
                        style={playMiniBtn}
                        onClick={() => playTTS(item.qa, 'us')}
                        title="Play answer (US)"
                      >🔊</button>
                      <span style={{ color: tokens['on-primary-container'], fontWeight: 500 }}>"{item.qa}"</span>
                    </div>
                    <div style={{ color: tokens['on-surface-variant'], paddingLeft: '1.75rem', ...typescale['body-small'] }}>
                      {item.qn}
                    </div>
                  </td>
                  <td style={{ padding: '0.85rem', textAlign: 'center' }}>
                    <select
                      value={status}
                      onChange={e => onStatusChange(item.id, e.target.value as MasteryStatus)}
                      style={{
                        background: 'transparent', border: 'none',
                        color: statusColor(status),
                        ...typescale['label-medium'], fontWeight: 700, cursor: 'pointer',
                      }}
                    >
                      <option value="unattempted" style={{ background: '#0B0F19' }}>Unattempted</option>
                      <option value="review"      style={{ background: '#0B0F19', color: '#FBBF24' }}>Review</option>
                      <option value="mastered"    style={{ background: '#0B0F19', color: '#10B981' }}>Mastered</option>
                    </select>
                  </td>
                </tr>
              );
            })}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={5} style={{ padding: '2.5rem', textAlign: 'center', color: tokens['on-surface-variant'], fontStyle: 'italic' }}>
                  No matches found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem' }}>
          <span style={{ ...typescale['body-small'], color: tokens['on-surface-variant'] }}>
            Showing {start + 1}–{Math.min(start + ITEMS_PER_PAGE, filtered.length)} of {filtered.length}
          </span>
          <div style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
            <button
              disabled={currentPage === 1}
              onClick={() => setCurrentPage(p => Math.max(p - 1, 1))}
              style={{
                ...typescale['label-medium'], padding: '0.3rem 0.7rem', borderRadius: '8px', cursor: 'pointer',
                background: tokens['surface-container-high'], border: `1px solid ${tokens['outline-variant']}`,
                color: currentPage === 1 ? tokens['on-surface-variant'] : tokens['on-surface'],
                opacity: currentPage === 1 ? 0.5 : 1,
              }}
            >←</button>
            <span style={{ ...typescale['body-small'], color: tokens['on-surface-variant'], padding: '0 0.5rem' }}>
              {currentPage} / {totalPages}
            </span>
            <button
              disabled={currentPage === totalPages}
              onClick={() => setCurrentPage(p => Math.min(p + 1, totalPages))}
              style={{
                ...typescale['label-medium'], padding: '0.3rem 0.7rem', borderRadius: '8px', cursor: 'pointer',
                background: tokens['surface-container-high'], border: `1px solid ${tokens['outline-variant']}`,
                color: currentPage === totalPages ? tokens['on-surface-variant'] : tokens['on-surface'],
                opacity: currentPage === totalPages ? 0.5 : 1,
              }}
            >→</button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExplorerPage;
