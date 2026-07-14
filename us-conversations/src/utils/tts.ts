/**
 * tts.ts — toeic-app バックエンドの OpenAI TTS を使用した音声再生
 *
 * エンドポイント: VITE_STT_BASE_URL/tts
 * リクエスト: POST { text: string }
 * レスポンス: audio/mpeg ストリーム → Howler で再生
 *
 * toeic-app の実装 (App.tsx handlePlayTTS) と同じパターン。
 */
import { Howl } from 'howler';

const TTS_ENDPOINT =
  (import.meta.env.VITE_STT_BASE_URL ?? 'https://api-toeic-app-591803230494.us-central1.run.app') +
  '/tts';

/** 現在再生中の Howl インスタンス */
let _currentHowl: Howl | null = null;

/** バックエンド TTS を停止 */
export function stopBackendTTS() {
  if (_currentHowl) {
    _currentHowl.stop();
    _currentHowl.unload();
    _currentHowl = null;
  }
}

/**
 * toeic-app の /tts エンドポイントでテキストを音声合成し Howler で再生。
 *
 * @param text      読み上げるテキスト
 * @param onEnd     再生終了コールバック
 * @param onError   エラーコールバック
 */
export async function playTTSBackend(
  text: string,
  onEnd?: () => void,
  onError?: (msg: string) => void
): Promise<void> {
  if (!text.trim()) return;

  // 既存の再生を止める
  stopBackendTTS();

  try {
    const response = await fetch(TTS_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });

    if (!response.ok) {
      throw new Error(`TTS server error: ${response.status} ${response.statusText}`);
    }

    const blob = await response.blob();
    const url  = URL.createObjectURL(blob);

    _currentHowl = new Howl({
      src: [url],
      format: ['mp3'],
      html5: true,
      onend: () => {
        URL.revokeObjectURL(url);
        _currentHowl = null;
        onEnd?.();
      },
      onloaderror: (_id, err) => {
        URL.revokeObjectURL(url);
        _currentHowl = null;
        console.error('[tts] load error:', err);
        onError?.('音声の読み込みに失敗しました。');
      },
      onplayerror: (_id, err) => {
        _currentHowl = null;
        console.error('[tts] play error:', err);
        onError?.('音声の再生に失敗しました。');
      },
    });

    _currentHowl.play();
  } catch (err) {
    console.error('[tts] fetch error:', err);
    onError?.(err instanceof Error ? err.message : 'TTS リクエストに失敗しました。');
  }
}
