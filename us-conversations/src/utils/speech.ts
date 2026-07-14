// Browser Speech Synthesis (Text-to-Speech) & Speech Recognition (Speech-to-Text) wrapper

export interface VoiceOption {
  name: string;
  lang: string;
  gender?: 'male' | 'female';
}

export function getAvailableVoices(): SpeechSynthesisVoice[] {
  if (typeof window === 'undefined' || !window.speechSynthesis) return [];
  return window.speechSynthesis.getVoices();
}

let currentUtterance: SpeechSynthesisUtterance | null = null;

export function speakText(
  text: string,
  voiceLang: 'en-US' | 'en-GB' | 'ja-JP',
  voiceName?: string,
  onEnd?: () => void,
  rate = 1.0
) {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    console.warn('Speech synthesis not supported');
    if (onEnd) onEnd();
    return;
  }

  // Cancel current speech if speaking
  window.speechSynthesis.cancel();
  if (currentUtterance) {
    currentUtterance.onend = null;
  }

  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = voiceLang;
  utterance.rate = Math.max(0.5, Math.min(2.0, rate));
  
  // Find custom voice if specified
  const voices = getAvailableVoices();
  if (voiceName) {
    const selectedVoice = voices.find(v => v.name === voiceName);
    if (selectedVoice) utterance.voice = selectedVoice;
  } else {
    // Automatic fallback logic based on lang
    let matchedVoice: SpeechSynthesisVoice | undefined;
    if (voiceLang === 'en-US') {
      // Prefer Google US English, Samantha, or male voices for John
      matchedVoice = voices.find(v => v.lang.startsWith('en-US') && (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('David')));
      if (!matchedVoice) matchedVoice = voices.find(v => v.lang.startsWith('en-US'));
    } else if (voiceLang === 'en-GB') {
      // Prefer Hazel, Google UK English, or female voices for Emily
      matchedVoice = voices.find(v => v.lang.startsWith('en-GB') && (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('Hazel') || v.name.includes('Susan')));
      if (!matchedVoice) matchedVoice = voices.find(v => v.lang.startsWith('en-GB'));
    }
    
    if (matchedVoice) {
      utterance.voice = matchedVoice;
    }
  }

  utterance.onend = () => {
    currentUtterance = null;
    if (onEnd) onEnd();
  };

  utterance.onerror = (e) => {
    console.error('Speech synthesis error', e);
    currentUtterance = null;
    if (onEnd) onEnd();
  };

  currentUtterance = utterance;
  window.speechSynthesis.speak(utterance);
}

export function stopSpeaking() {
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
}

// Browser Speech Recognition Wrapper
export interface SpeechRecognitionResultCallback {
  (transcript: string, isFinal: boolean): void;
}

export function createSpeechRecognizer(
  lang: 'en-US' | 'en-GB',
  onResult: SpeechRecognitionResultCallback,
  onEnd: () => void,
  onError: (error: string) => void
) {
  const SpeechRecognitionAPI =
    (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

  if (!SpeechRecognitionAPI) {
    return null;
  }

  const recognition = new SpeechRecognitionAPI();
  recognition.continuous = true;
  recognition.interimResults = true;
  recognition.lang = lang;

  recognition.onresult = (event: any) => {
    let interimTranscript = '';
    let finalTranscript = '';

    for (let i = 0; i < event.results.length; ++i) {
      if (event.results[i].isFinal) {
        finalTranscript += event.results[i][0].transcript + ' ';
      } else {
        interimTranscript += event.results[i][0].transcript;
      }
    }

    const fullTranscript = (finalTranscript + interimTranscript).trim();
    onResult(fullTranscript, finalTranscript !== '');
  };

  recognition.onerror = (event: any) => {
    console.error('Speech Recognition Error:', event.error);
    onError(event.error);
  };

  recognition.onend = () => {
    onEnd();
  };

  return recognition;
}
