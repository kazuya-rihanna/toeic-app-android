/**
 * whisper.ts — Whisper API 音声認識ユーティリティ
 *
 * toeic-app の既存バックエンド (/stt) を使用。
 * MediaRecorder で録音 → WebM blob → POST → transcribed_text を返す。
 *
 * エンドポイント: VITE_STT_BASE_URL/stt
 * レスポンス: { "transcribed_text": "..." }
 */

const STT_ENDPOINT =
  (import.meta.env.VITE_STT_BASE_URL ?? 'https://api-toeic-app-591803230494.us-central1.run.app') +
  '/stt';

/** startWhisperRecording() の戻り値 */
export interface WhisperSession {
  /** 録音を停止し Whisper へ送信する */
  stop: () => void;
}

/**
 * マイク録音を開始し WhisperSession を返す。
 * stopSession.stop() を呼ぶと録音停止 → サーバー送信 → onTranscript コールバック。
 *
 * @param onTranscript  文字起こし完了時のコールバック (text)
 * @param onEnd         録音終了直後（処理中）のコールバック
 * @param onError       エラー時のコールバック (message)
 */
export async function startWhisperRecording(
  onTranscript: (text: string) => void,
  onEnd: () => void,
  onError: (message: string) => void
): Promise<WhisperSession | null> {
  let stream: MediaStream;

  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (err) {
    onError('マイクへのアクセスが拒否されました。ブラウザの権限設定を確認してください。');
    return null;
  }

  let mediaRecorder: MediaRecorder;
  try {
    const mimeType =
      MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' :
      MediaRecorder.isTypeSupported('audio/webm')             ? 'audio/webm'             :
      MediaRecorder.isTypeSupported('audio/mp4')              ? 'audio/mp4'              :
      ''; // fallback to browser default

    const options = mimeType ? { mimeType } : undefined;
    mediaRecorder = new MediaRecorder(stream, options);
  } catch (err) {
    console.error('MediaRecorder initialization failed:', err);
    onError('お使いのブラウザは録音をサポートしていない可能性があります。');
    return null;
  }

  const chunks: Blob[] = [];

  mediaRecorder.ondataavailable = (e) => {
    if (e.data.size > 0) chunks.push(e.data);
  };

  mediaRecorder.onstop = async () => {
    // マイクを解放
    stream.getTracks().forEach(t => t.stop());
    onEnd();

    // The browser-selected mime type
    const finalMime = mediaRecorder.mimeType || 'audio/webm';
    const blob = new Blob(chunks, { type: finalMime });

    // Whisper が受け付けるファイル名に合わせる
    const ext = finalMime.includes('mp4') ? 'mp4' : finalMime.includes('ogg') ? 'ogg' : 'webm';
    const formData = new FormData();
    formData.append('audio', blob, `recording.${ext}`);

    try {
      const res = await fetch(STT_ENDPOINT, {
        method: 'POST',
        body: formData,
      });

      if (!res.ok) {
        const text = await res.text().catch(() => res.statusText);
        throw new Error(`Server ${res.status}: ${text}`);
      }

      const data: { transcribed_text?: string } = await res.json();
      onTranscript(data.transcribed_text ?? '');
    } catch (err) {
      console.error('[whisper] transcription error:', err);
      onError(err instanceof Error ? err.message : 'Whisper 送信に失敗しました。');
    }
  };

  // 500ms ごとにデータを収集（短い録音でも確実に取得）
  mediaRecorder.start(500);

  return {
    stop() {
      if (mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
      }
    },
  };
}
