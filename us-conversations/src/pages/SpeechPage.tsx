import React, { useState, useRef } from 'react';
import { FULL_SPEECH } from '../types';
import { stopSpeaking } from '../utils/speech';
import { playTTSBackend, stopBackendTTS } from '../utils/tts';
import { startWhisperRecording } from '../utils/whisper';
import type { WhisperSession } from '../utils/whisper';
import { getWordDiff, getSimilarityScore } from '../utils/diff';
import type { DiffToken } from '../utils/diff';
import { typescale, elevation, motionTokens } from '../theme/tokens';

interface SpeechPageProps {
  tokens: Record<string, string>;
}

type RecordingState = 'idle' | 'starting' | 'recording' | 'processing';

export const SpeechPage: React.FC<SpeechPageProps> = ({ tokens }) => {
  const [isSpeakingUs, setIsSpeakingUs]         = useState(false);
  const [isSpeakingUk, setIsSpeakingUk]         = useState(false);
  const [recordingState, setRecordingState]      = useState<RecordingState>('idle');
  const [spokenText, setSpokenText]              = useState('');
  const [similarity, setSimilarity]              = useState<number | null>(null);
  const [diff, setDiff]                          = useState<DiffToken[]>([]);
  const [error, setError]                        = useState<string | null>(null);
  const sessionRef = useRef<WhisperSession | null>(null);

  // ---- Styles ----
  const glassCard: React.CSSProperties = {
    background: tokens['surface-container'],
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: `1px solid ${tokens['outline-variant']}`,
    borderRadius: '16px',
    ...elevation.level2,
    padding: '2rem',
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
  const scoreColor = (s: number) =>
    s >= 85 ? tokens['primary'] : s >= 60 ? '#FBBF24' : tokens['error'];

  // ---- TTS handlers (OpenAI backend) ----
  const handlePlayUs = () => {
    stopBackendTTS(); setIsSpeakingUs(true); setIsSpeakingUk(false);
    playTTSBackend(FULL_SPEECH.english, () => setIsSpeakingUs(false), () => setIsSpeakingUs(false));
  };
  const handlePlayUk = () => {
    stopBackendTTS(); setIsSpeakingUk(true); setIsSpeakingUs(false);
    playTTSBackend(FULL_SPEECH.english, () => setIsSpeakingUk(false), () => setIsSpeakingUk(false));
  };
  const handleStop = () => { stopSpeaking(); stopBackendTTS(); setIsSpeakingUs(false); setIsSpeakingUk(false); };

  // ---- Whisper recording ----
  const startRecording = async () => {
    if (recordingState !== 'idle') return;
    setError(null);
    setSpokenText('');
    setSimilarity(null);
    setDiff([]);
    setRecordingState('starting');
    sessionRef.current = null;

    const session = await startWhisperRecording(
      // onTranscript
      (text) => {
        setSpokenText(text);
        setSimilarity(getSimilarityScore(FULL_SPEECH.english, text));
        setDiff(getWordDiff(FULL_SPEECH.english, text));
        setRecordingState('idle');
      },
      // onEnd (録音停止直後 → 処理中)
      () => setRecordingState('processing'),
      // onError
      (msg) => { setError(msg); setRecordingState('idle'); }
    );

    if (!session) {
      setRecordingState('idle');
    } else {
      sessionRef.current = session;
      setRecordingState('recording');
    }
  };

  const stopRecording = () => {
    sessionRef.current?.stop();
    // state は onEnd コールバックで 'processing' に切り替わる
  };

  // ---- Record button label/style by state ----
  const RecordButton = () => {
    if (recordingState === 'idle') {
      return (
        <button style={{ ...btnPrimary, padding: '0.85rem 2.5rem', borderRadius: '50px', fontSize: '1.05rem', gap: '0.6rem' }}
          onClick={startRecording}>
          <span style={{ fontSize: '1.2rem' }}>🎙️</span> Start Speaking
        </button>
      );
    }
    if (recordingState === 'starting') {
      return (
        <button disabled style={{ ...btnSecondary, padding: '0.85rem 2.5rem', borderRadius: '50px', fontSize: '1.05rem', opacity: 0.8, cursor: 'default', display: 'flex', gap: '0.65rem', alignItems: 'center' }}>
          <span style={{ display: 'inline-block', width: '16px', height: '16px', border: `2px solid ${tokens['primary']}`, borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
          Starting Mic…
        </button>
      );
    }
    if (recordingState === 'recording') {
      return (
        <button className="animate-pulse"
          style={{ ...btnPrimary, padding: '0.85rem 2.5rem', borderRadius: '50px', fontSize: '1.05rem', background: '#DC2626', display: 'flex', gap: '0.65rem', alignItems: 'center' }}
          onClick={stopRecording}>
          <div className="recording-wave" style={{ width: '16px', height: '16px', background: '#fff' }} />
          Stop Recording
        </button>
      );
    }
    // processing
    return (
      <button disabled style={{ ...btnSecondary, padding: '0.85rem 2.5rem', borderRadius: '50px', fontSize: '1.05rem', opacity: 0.8, cursor: 'default', display: 'flex', gap: '0.65rem', alignItems: 'center' }}>
        <span style={{ display: 'inline-block', width: '16px', height: '16px', border: `2px solid ${tokens['primary']}`, borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        Transcribing with Whisper…
      </button>
    );
  };

  return (
    <>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem' }} className="animate-fade-in speech-grid">

        {/* Left: Speech text + TTS */}
        <div style={glassCard}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '0.75rem' }}>
            <h2 style={{ ...typescale['title-large'], color: tokens['on-surface'], margin: 0 }}>
              🎤 Base Self-Introduction
            </h2>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <button style={{ ...btnSecondary, color: isSpeakingUs ? tokens['primary'] : tokens['on-surface'] }} onClick={handlePlayUs}>
                {isSpeakingUs ? '🔊 Speaking…' : '🔊 US (John)'}
              </button>
              <button style={{ ...btnSecondary, color: isSpeakingUk ? tokens['primary'] : tokens['on-surface'] }} onClick={handlePlayUk}>
                {isSpeakingUk ? '🔊 Speaking…' : '🔊 UK (Emily)'}
              </button>
              <button style={{ ...btnSecondary, color: tokens['error'] }} onClick={handleStop}>⏹ Stop</button>
            </div>
          </div>

          <div style={{
            background: tokens['surface-container-lowest'],
            border: `1px solid ${tokens['outline-variant']}`,
            borderRadius: '12px', padding: '1.25rem',
            ...typescale['body-large'], lineHeight: 1.85,
            color: tokens['on-surface'], whiteSpace: 'pre-line', marginBottom: '1.5rem',
          }}>
            {FULL_SPEECH.english}
          </div>

          <div style={{ borderTop: `1px solid ${tokens['outline-variant']}`, paddingTop: '1.25rem' }}>
            <h3 style={{ ...typescale['title-small'], color: tokens['on-surface'], marginBottom: '0.65rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              🇯🇵 Japanese Translation
            </h3>
            <p style={{ ...typescale['body-medium'], color: tokens['on-surface-variant'], lineHeight: 1.75 }}>
              {FULL_SPEECH.japanese}
            </p>
          </div>
        </div>

        {/* Right: Whisper practice */}
        <div style={{ ...glassCard, display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div>
            <h2 style={{ ...typescale['title-large'], color: tokens['on-surface'], margin: 0 }}>
              🎙️ Speaking Practice
            </h2>
            <p style={{ ...typescale['body-small'], color: tokens['on-surface-variant'], marginTop: '0.4rem', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <span style={{ background: tokens['primary-container'], color: tokens['on-primary-container'], padding: '0.1rem 0.45rem', borderRadius: '4px', ...typescale['label-small'], fontWeight: 700 }}>
                Whisper-1
              </span>
              powered by toeic-app backend
            </p>
          </div>

          <p style={{ ...typescale['body-medium'], color: tokens['on-surface-variant'], marginTop: '-0.75rem' }}>
            スピーチを読み上げて Stop を押してください。Whisper が文字起こしし、精度をスコアリングします。
          </p>

          {/* Record button */}
          <div style={{ display: 'flex', justifyContent: 'center', padding: '0.5rem 0' }}>
            <RecordButton />
          </div>

          {/* Error */}
          {error && (
            <div style={{ ...typescale['body-small'], color: tokens['error'], background: tokens['error-container'], border: `1px solid ${tokens['error']}40`, borderRadius: '8px', padding: '0.65rem 0.85rem' }}>
              ⚠️ {error}
            </div>
          )}

          {/* Transcription box */}
          <div style={{ flexGrow: 1 }}>
            <h3 style={{ ...typescale['label-large'], color: tokens['on-surface-variant'], marginBottom: '0.4rem' }}>
              Whisper Transcription:
            </h3>
            <div style={{
              minHeight: '120px',
              background: tokens['surface-container-lowest'],
              border: `1px solid ${recordingState === 'processing' ? tokens['primary'] : tokens['outline-variant']}`,
              borderRadius: '12px', padding: '1rem',
              ...typescale['body-medium'],
              color: spokenText ? tokens['on-surface'] : tokens['on-surface-variant'],
              fontStyle: spokenText ? 'normal' : 'italic',
              transition: `border-color ${motionTokens.duration.short4}`,
            }}>
              {recordingState === 'processing'
                ? <span style={{ color: tokens['primary'] }}>⏳ Whisper が解析中…</span>
                : spokenText || '録音後にここへ文字起こしが表示されます…'}
            </div>
          </div>

          {/* Analysis results */}
          {similarity !== null && (
            <div className="animate-fade-in" style={{ borderTop: `1px solid ${tokens['outline-variant']}`, paddingTop: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ ...typescale['title-small'], color: tokens['on-surface'] }}>Accuracy Score:</span>
                <span style={{ fontWeight: 800, fontSize: '2rem', color: scoreColor(similarity) }}>{similarity}%</span>
              </div>
              <div>
                <p style={{ ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.4rem' }}>
                  Visual Comparison:
                </p>
                <div style={{ background: tokens['surface-container-lowest'], padding: '0.9rem 1rem', borderRadius: '8px', ...typescale['body-medium'], lineHeight: 1.75 }}>
                  {diff.map((token, idx) => (
                    <span key={idx} className={`diff-char diff-${token.type}`}>{token.text}{' '}</span>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem', ...typescale['label-small'], color: tokens['on-surface-variant'] }}>
                  <span>⬜ Match</span>
                  <span style={{ color: tokens['error'] }}>🔴 Missing</span>
                  <span style={{ color: tokens['primary'] }}>🟢 Extra</span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
};

export default SpeechPage;
