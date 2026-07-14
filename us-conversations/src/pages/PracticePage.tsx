import React, { useState, useRef, useEffect } from 'react';
import type { Conversation, MasteryStatus, PracticeMode } from '../types';
import { CATEGORIES } from '../types';
import { stopSpeaking } from '../utils/speech';
import { playTTSBackend, stopBackendTTS } from '../utils/tts';
import { startWhisperRecording } from '../utils/whisper';
import type { WhisperSession } from '../utils/whisper';
import { getWordDiff, getSimilarityScore } from '../utils/diff';
import type { DiffToken } from '../utils/diff';
import { typescale, elevation, motionTokens } from '../theme/tokens';

interface PracticePageProps {
  conversations: Conversation[];
  progress: Record<number, MasteryStatus>;
  progressLoaded: boolean;
  onStatusChange: (id: number, status: MasteryStatus) => void;
  tokens: Record<string, string>;
  selectedCategory: string;
  setSelectedCategory: (cat: string) => void;
  onPlaySuccess: () => void;
  onPlayReview: () => void;
}

type RecordingState = 'idle' | 'recording' | 'processing';

export function PracticePage({
  conversations,
  progress,
  progressLoaded,
  onStatusChange,
  tokens,
  selectedCategory, setSelectedCategory,
  onPlaySuccess, onPlayReview,
}) {
  const [practiceMode, setPracticeMode]    = useState<PracticeMode>('speech');
  const [filteredCards, setFilteredCards]  = useState<Conversation[]>([]);
  const [cardIndex, setCardIndex]          = useState(0);
  const [showAnswer, setShowAnswer]        = useState(false); // For Flashcard mode
  const [showAnswerPeek, setShowAnswerPeek]= useState(false); // For Peek UI

  // Whisper recording state
  const [recordingState, setRecordingState] = useState<RecordingState>('idle');
  const [spokenText, setSpokenText]         = useState('');
  const [recogError, setRecogError]         = useState<string | null>(null);
  const sessionRef = useRef<WhisperSession | null>(null);

  // Correct Animation State
  const [showCorrectAnimation, setShowCorrectAnimation] = useState(false);

  // Partner Translation Toggle State
  const [showPartnerTranslation, setShowPartnerTranslation] = useState(false);

  // Typing mode
  const [typingInput, setTypingInput] = useState('');

  // Shared result
  const [similarity, setSimilarity]   = useState<number | null>(null);
  const [diffTokens, setDiffTokens]   = useState<DiffToken[]>([]);

  // TTS states
  const [isSpeakingQ, setIsSpeakingQ] = useState(false);
  const [isSpeakingA, setIsSpeakingA] = useState(false);

  // Auto-resume logic
  const hasJumpedRef = useRef(false);

  // When category changes, reset the jump flag so we can jump again
  useEffect(() => {
    hasJumpedRef.current = false;
  }, [selectedCategory]);

  // Filter cards and determine starting index
  useEffect(() => {
    const list = selectedCategory === 'all'
      ? [...conversations]
      : conversations.filter(c => c.cat === selectedCategory);
    setFilteredCards(list);

    // If progress is loaded and we haven't jumped yet for this category
    if (progressLoaded && !hasJumpedRef.current && list.length > 0) {
      let firstUnmastered = list.findIndex(c => progress[c.id] !== 'mastered');
      if (firstUnmastered === -1) firstUnmastered = 0; // all mastered
      setCardIndex(firstUnmastered);
      hasJumpedRef.current = true;
    } else if (!progressLoaded) {
      setCardIndex(0); // fallback before load
    }
    
    resetState();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCategory, conversations, progressLoaded, progress]);

  const resetState = () => {
    setShowAnswer(false);
    setShowAnswerPeek(false);
    setShowPartnerTranslation(false);
    setTypingInput('');
    setSpokenText('');
    setSimilarity(null);
    setDiffTokens([]);
    setRecogError(null);
    setRecordingState('idle');
    stopSpeaking();
    stopBackendTTS();
    setIsSpeakingQ(false);
    setIsSpeakingA(false);
    sessionRef.current?.stop();
    sessionRef.current = null;
  };

  const handleNext = () => { setCardIndex(prev => (prev + 1) % filteredCards.length); resetState(); };
  const handlePrev = () => { setCardIndex(prev => (prev - 1 + filteredCards.length) % filteredCards.length); resetState(); };

  const playTTS = (text: string, forPrompt: boolean) => {
    stopBackendTTS();
    if (forPrompt) { setIsSpeakingQ(true); setIsSpeakingA(false); }
    else           { setIsSpeakingA(true); setIsSpeakingQ(false); }
    playTTSBackend(
      text,
      () => { setIsSpeakingQ(false); setIsSpeakingA(false); },
      () => { setIsSpeakingQ(false); setIsSpeakingA(false); }
    );
  };

  // ---- Whisper recording ----
  const startRecording = async (target: string) => {
    setRecogError(null);
    setSpokenText('');
    setSimilarity(null);
    setDiffTokens([]);
    setRecordingState('recording');

    const session = await startWhisperRecording(
      (text) => {
        setSpokenText(text);
        const score = getSimilarityScore(target, text);
        setSimilarity(score);
        setDiffTokens(getWordDiff(target, text));
        setRecordingState('idle');

        // Auto-Mastered Logic
        if (score === 100) {
          handleMarkStatus(currentCard.id, 'mastered');
          setShowCorrectAnimation(true);
          setTimeout(() => {
            setShowCorrectAnimation(false);
            handleNext();
          }, 1500); // Wait 1.5s then advance
        }
      },
      () => setRecordingState('processing'),
      (msg) => { setRecogError(msg); setRecordingState('idle'); }
    );

    if (!session) {
      setRecordingState('idle');
    } else {
      sessionRef.current = session;
    }
  };

  const stopRecording = () => { sessionRef.current?.stop(); };

  const handleMarkStatus = (id: number, status: MasteryStatus) => {
    onStatusChange(id, status);
    if (status === 'mastered') onPlaySuccess();
    else if (status === 'review') onPlayReview();
  };

  // ---- Styles ----
  const glassCard: React.CSSProperties = {
    background: tokens['surface-container'], borderRadius: '12px',
    border: `1px solid ${tokens['outline-variant']}`,
    color: tokens['on-surface'],
  };
  const btnBase: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    padding: '0.5rem 1rem', borderRadius: '10px', cursor: 'pointer',
    fontFamily: 'inherit', ...typescale['label-large'], border: '1px solid transparent',
    transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
  };
  const btnSecondary: React.CSSProperties = {
    ...btnBase, background: tokens['surface-container-high'],
    border: `1px solid ${tokens['outline-variant']}`, color: tokens['on-surface'],
  };
  const btnPrimary: React.CSSProperties = {
    ...btnBase,
    background: `linear-gradient(135deg, #059669, ${tokens['primary']})`,
    color: '#fff', boxShadow: `0 4px 12px ${tokens['primary-container']}`,
  };
  const toggleBtn = (active: boolean): React.CSSProperties => ({
    ...btnBase, padding: '0.4rem 0.9rem', fontSize: '0.85rem',
    background: active ? tokens['primary-container'] : 'transparent',
    border: `1px solid ${active ? tokens['primary'] : tokens['outline-variant']}`,
    color: active ? tokens['on-primary-container'] : tokens['on-surface-variant'],
  });
  const scoreColor = (s: number) =>
    s >= 85 ? tokens['primary'] : s >= 60 ? '#FBBF24' : tokens['error'];

  const spinnerStyle: React.CSSProperties = {
    display: 'inline-block', width: '14px', height: '14px',
    border: `2px solid ${tokens['primary']}`,
    borderTopColor: 'transparent', borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
  };

  const currentCard = filteredCards[cardIndex];

  return (
    <>
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        @keyframes popIn {
          0% { transform: scale(0.5); opacity: 0; }
          80% { transform: scale(1.1); opacity: 1; }
          100% { transform: scale(1); opacity: 1; }
        }
        @keyframes slideUpFade {
          0% { transform: translateY(20px); opacity: 0; }
          100% { transform: translateY(0); opacity: 1; }
        }
      `}</style>
      <div style={{ maxWidth: '820px', margin: '0 auto', position: 'relative' }} className="animate-fade-in">

        {/* Correct Animation Overlay */}
        {showCorrectAnimation && (
          <div style={{
            position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, zIndex: 50,
            background: 'rgba(52, 211, 153, 0.2)', // Light green tint
            backdropFilter: 'blur(8px)',
            borderRadius: '16px',
            display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center',
            animation: 'fadeIn 0.3s ease-out'
          }}>
            <div style={{
              fontSize: '8rem',
              animation: 'popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards',
              textShadow: '0 10px 25px rgba(5, 150, 105, 0.5)'
            }}>
              ✅
            </div>
            <h2 style={{
              ...typescale['headline-large'],
              color: '#059669',
              marginTop: '1rem',
              animation: 'slideUpFade 0.5s ease-out forwards',
              animationDelay: '0.1s',
              opacity: 0,
            }}>
              Perfect!
            </h2>
          </div>
        )}

        {/* Filter + Mode bar */}
        <div style={{
          ...glassCard, padding: '0.5rem 0.75rem', marginBottom: '0.75rem',
          display: 'flex', justifyContent: 'space-between',
          alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem',
        }}>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <span style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'] }}>Category:</span>
            <select value={selectedCategory} onChange={e => setSelectedCategory(e.target.value)}
              style={{ background: tokens['surface-container-lowest'], border: `1px solid ${tokens['outline-variant']}`, borderRadius: '8px', padding: '0.35rem 0.65rem', color: tokens['on-surface'], ...typescale['body-medium'], cursor: 'pointer' }}>
              {CATEGORIES.map(cat => <option key={cat.id} value={cat.id}>{cat.label}</option>)}
            </select>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {(['speech', 'typing', 'flashcard'] as PracticeMode[]).map(mode => (
              <button key={mode} style={toggleBtn(practiceMode === mode)}
                onClick={() => { setPracticeMode(mode); resetState(); }}>
                {mode === 'speech' ? '🎙️ Speech' : mode === 'typing' ? '⌨️ Typing' : '🃏 Flashcard'}
              </button>
            ))}
          </div>
        </div>

        {filteredCards.length > 0 && currentCard ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ ...glassCard, padding: '1rem 1.25rem' }}>

              {/* Card header */}
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.75rem', paddingBottom: '0.5rem', borderBottom: `1px solid ${tokens['outline-variant']}` }}>
                <span style={{ ...typescale['label-medium'], color: tokens['primary'], fontWeight: 700, textTransform: 'uppercase' }}>
                  Q&amp;A {String(currentCard.id).padStart(3, '0')} — {currentCard.cat}
                </span>
                <span style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'] }}>
                  {cardIndex + 1} / {filteredCards.length}
                </span>
              </div>

              {/* Question */}
              <div style={{ marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' }}>
                  <span style={{ ...typescale['label-large'], color: tokens['on-surface'], fontWeight: 700 }}>🗣️ Partner:</span>
                  <div style={{ display: 'flex', gap: '0.4rem' }}>
                    <button
                      onClick={() => setShowPartnerTranslation(!showPartnerTranslation)}
                      style={{ ...btnSecondary, padding: '0.2rem 0.65rem', fontSize: '0.78rem' }}
                    >
                      {showPartnerTranslation ? '▲ Hide' : '▼ A/あ Translate'}
                    </button>
                    <button
                      style={{ ...btnSecondary, padding: '0.2rem 0.65rem', fontSize: '0.78rem', color: isSpeakingQ ? tokens['primary'] : tokens['on-surface'], gap: '0.3rem', display: 'inline-flex', alignItems: 'center' }}
                      onClick={() => isSpeakingQ ? (stopBackendTTS(), setIsSpeakingQ(false)) : playTTS(currentCard.q, true)}
                    >
                      {isSpeakingQ
                        ? <><span style={{ display: 'inline-block', width: '10px', height: '10px', border: `2px solid ${tokens['primary']}`, borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} /> Stop</>  
                        : '🔊 Play'}
                    </button>
                  </div>
                </div>
                <div style={{ fontSize: '1rem', fontWeight: 500, lineHeight: 1.4, color: tokens['on-surface'], background: tokens['surface-container-high'], padding: '0.6rem 0.75rem', borderRadius: '8px', borderLeft: `3px solid ${tokens['primary']}` }}>
                  "{currentCard.q}"
                  
                  {/* Inline Translation */}
                  {showPartnerTranslation && currentCard.q_ja && (
                    <div className="animate-fade-in" style={{
                      marginTop: '0.75rem',
                      paddingTop: '0.75rem',
                      borderTop: `1px dashed ${tokens['outline-variant']}`,
                      ...typescale['body-medium'],
                      color: tokens['on-surface-variant'],
                      fontWeight: 400
                    }}>
                      🇯🇵 {currentCard.q_ja}
                    </div>
                  )}
                </div>
              </div>

              {/* Japanese hint (常時表示) */}
              <div style={{ marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' }}>
                  <span style={{ ...typescale['label-large'], color: tokens['on-surface'], fontWeight: 700 }}>🇯🇵 Meaning to Say:</span>
                </div>
                <p style={{ ...typescale['body-medium'], color: tokens['on-surface-variant'], lineHeight: 1.4, margin: 0 }}>
                  {currentCard.qn}
                </p>
              </div>

              {/* English Target (チラ見UI) */}
              <div style={{ marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' }}>
                  <span style={{ ...typescale['label-large'], color: tokens['on-surface'], fontWeight: 700 }}>🇺🇸 Target Answer:</span>
                  <div style={{ display: 'flex', gap: '0.4rem' }}>
                    <button
                      onMouseDown={() => setShowAnswerPeek(true)}
                      onMouseUp={() => setShowAnswerPeek(false)}
                      onMouseLeave={() => setShowAnswerPeek(false)}
                      onTouchStart={() => setShowAnswerPeek(true)}
                      onTouchEnd={() => setShowAnswerPeek(false)}
                      style={{ ...btnSecondary, padding: '0.2rem 0.55rem', fontSize: '0.75rem', userSelect: 'none', WebkitUserSelect: 'none' }}
                    >
                      👁 Hold to Peek
                    </button>
                  </div>
                </div>
                <div style={{
                  position: 'relative', overflow: 'hidden',
                  background: tokens['surface-container-high'],
                  padding: '0.6rem 0.75rem', borderRadius: '8px', borderLeft: `3px solid ${tokens['primary']}`,
                  ...typescale['body-medium'], color: tokens['on-surface'], lineHeight: 1.4,
                  transition: `filter ${motionTokens.duration.medium2} ${motionTokens.easing.standard}`,
                  filter: showAnswerPeek ? 'none' : 'blur(6px)',
                  cursor: 'default',
                  userSelect: showAnswerPeek ? 'text' : 'none',
                }}>
                  {currentCard.qa}
                </div>
              </div>

              {/* Input area */}
              <div style={{ borderTop: `1px solid ${tokens['outline-variant']}`, paddingTop: '1.75rem' }}>

                {/* Speech mode (Whisper) */}
                {/* User Practice Section */}
                {practiceMode === 'speech' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'center' }}>
                      {recordingState === 'idle' && (
                        <button style={{ ...btnPrimary, padding: '0.7rem 1.85rem', borderRadius: '50px', gap: '0.5rem' }}
                          onClick={() => startRecording(currentCard.qa)}>
                          🎙️ Tap to Speak Answer
                        </button>
                      )}
                      {recordingState === 'recording' && (
                        <button className="animate-pulse"
                          style={{ ...btnPrimary, padding: '0.7rem 1.85rem', borderRadius: '50px', background: '#DC2626', display: 'flex', gap: '0.5rem', alignItems: 'center' }}
                          onClick={stopRecording}>
                          <div className="recording-wave" style={{ width: '14px', height: '14px', background: '#fff' }} />
                          Stop — Send to Whisper
                        </button>
                      )}
                      {recordingState === 'processing' && (
                        <button disabled style={{ ...btnSecondary, padding: '0.7rem 1.85rem', borderRadius: '50px', display: 'flex', gap: '0.5rem', alignItems: 'center', opacity: 0.8, cursor: 'default' }}>
                          <span style={spinnerStyle} />
                          Whisper 解析中…
                        </button>
                      )}
                    </div>

                    {recogError && (
                      <div style={{ ...typescale['body-small'], color: tokens['error'], background: tokens['error-container'], border: `1px solid ${tokens['error']}40`, borderRadius: '8px', padding: '0.6rem 0.8rem' }}>
                        ⚠️ {recogError}
                      </div>
                    )}

                    <div>
                      <p style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.3rem' }}>
                        Whisper Transcription:
                      </p>
                      <div style={{
                        minHeight: '56px', background: tokens['surface-container-lowest'],
                        border: `1px solid ${recordingState === 'processing' ? tokens['primary'] : tokens['outline-variant']}`,
                        borderRadius: '8px', padding: '0.65rem 0.85rem',
                        ...typescale['body-medium'],
                        color: spokenText ? tokens['on-surface'] : tokens['on-surface-variant'],
                        fontStyle: spokenText ? 'normal' : 'italic',
                        transition: `border-color ${motionTokens.duration.short4}`,
                      }}>
                        {recordingState === 'processing'
                          ? <span style={{ color: tokens['primary'] }}>⏳ 解析中…</span>
                          : spokenText || '録音後に文字起こしが表示されます…'}
                      </div>
                    </div>
                  </div>
                )}

                {/* Typing mode */}
                {practiceMode === 'typing' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    <p style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'] }}>Type English Answer:</p>
                    <textarea rows={3} value={typingInput} onChange={e => setTypingInput(e.target.value)}
                      placeholder="Type your response…"
                      style={{ width: '100%', padding: '0.65rem 0.85rem', borderRadius: '8px', background: tokens['surface-container-lowest'], border: `1px solid ${tokens['outline-variant']}`, color: tokens['on-surface'], resize: 'none', ...typescale['body-medium'], outline: 'none', fontFamily: 'inherit' }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <button style={btnPrimary}
                        onClick={() => {
                          setSimilarity(getSimilarityScore(currentCard.qa, typingInput));
                          setDiffTokens(getWordDiff(currentCard.qa, typingInput));
                        }}>
                        Check Typing ✓
                      </button>
                    </div>
                  </div>
                )}

                {/* Flashcard mode */}
                {practiceMode === 'flashcard' && (
                  <div style={{ display: 'flex', justifyContent: 'center', padding: '0.75rem 0' }}>
                    {!showAnswer
                      ? <button style={btnPrimary} onClick={() => setShowAnswer(true)}>Reveal English Answer</button>
                      : <button style={btnSecondary} onClick={() => setShowAnswer(false)}>Hide English Answer</button>
                    }
                  </div>
                )}

                {/* Results */}
                {((practiceMode !== 'flashcard' && similarity !== null) ||
                  (practiceMode === 'flashcard' && showAnswer)) && (
                  <div className="animate-fade-in" style={{ marginTop: '1.75rem', borderTop: `1px solid ${tokens['outline-variant']}`, paddingTop: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>

                    {practiceMode !== 'flashcard' && similarity !== null && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ ...typescale['body-medium'], color: tokens['on-surface-variant'] }}>Similarity Match:</span>
                        <span style={{ fontWeight: 800, fontSize: '1.75rem', color: scoreColor(similarity) }}>{similarity}%</span>
                      </div>
                    )}

                    {practiceMode !== 'flashcard' && diffTokens.length > 0 && (
                      <div>
                        <p style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.35rem' }}>Word Analysis:</p>
                        <div style={{ background: tokens['surface-container-lowest'], padding: '0.7rem 0.85rem', borderRadius: '8px', ...typescale['body-medium'], lineHeight: 1.7 }}>
                          {diffTokens.map((tok, idx) => (
                            <span key={idx} className={`diff-char diff-${tok.type}`}>{tok.text}{' '}</span>
                          ))}
                        </div>
                      </div>
                    )}

                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                        <span style={{ ...typescale['label-large'], color: tokens['on-surface-variant'] }}>Target Answer:</span>
                        <button style={{ ...btnSecondary, padding: '0.15rem 0.45rem', fontSize: '0.72rem', color: isSpeakingA ? tokens['primary'] : tokens['on-surface'], display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}
                          onClick={() => isSpeakingA ? (stopBackendTTS(), setIsSpeakingA(false)) : playTTS(currentCard.qa, false)}>
                          {isSpeakingA
                            ? <><span style={{ display: 'inline-block', width: '10px', height: '10px', border: `2px solid ${tokens['primary']}`, borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} /> Stop</>
                            : '🔊 Listen (OpenAI TTS)'}
                        </button>
                      </div>
                      <div style={{ ...typescale['body-large'], fontWeight: 500, color: tokens['on-primary-container'], background: tokens['primary-container'], border: `1px solid ${tokens['primary']}40`, padding: '0.65rem 0.85rem', borderRadius: '8px' }}>
                        {currentCard.qa}
                      </div>
                    </div>

                    {/* Mastery buttons */}
                    <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.25rem' }}>
                      <button
                        style={{ ...btnBase, flex: 1, background: progress[currentCard.id] === 'review' ? 'rgba(251,191,36,0.12)' : tokens['surface-container-high'], border: `1px solid ${progress[currentCard.id] === 'review' ? '#FBBF24' : tokens['outline-variant']}`, color: '#FBBF24' }}
                        onClick={() => handleMarkStatus(currentCard.id, 'review')}>
                        ⚠️ Needs Practice
                      </button>
                      <button
                        style={{ ...btnPrimary, flex: 1, background: progress[currentCard.id] === 'mastered' ? tokens['primary'] : `linear-gradient(135deg, #059669, ${tokens['primary']})` }}
                        onClick={() => handleMarkStatus(currentCard.id, 'mastered')}>
                        ✓ Mastered
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Navigation */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button style={btnSecondary} onClick={handlePrev}>← Previous</button>
              <div style={{ ...glassCard, padding: '0.35rem 1rem', borderRadius: '20px', ...typescale['body-small'], color: tokens['on-surface-variant'] }}>
                Status:{' '}
                <span style={{ fontWeight: 700, color: progress[currentCard.id] === 'mastered' ? tokens['primary'] : progress[currentCard.id] === 'review' ? '#FBBF24' : tokens['on-surface-variant'] }}>
                  {progress[currentCard.id] === 'mastered' ? 'Mastered' : progress[currentCard.id] === 'review' ? 'Needs Review' : 'Unattempted'}
                </span>
              </div>
              <button style={btnPrimary} onClick={handleNext}>Next →</button>
            </div>
          </div>
        ) : (
          <div style={{ ...glassCard, padding: '3rem', textAlign: 'center', color: tokens['on-surface-variant'] }}>
            No cards match the selected category.
          </div>
        )}
      </div>
    </>
  );
};

export default PracticePage;
