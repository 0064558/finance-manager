import { DOCUMENT } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { LucideMic, LucideSquare } from '@lucide/angular';

// The recognition events are in the DOM types, but the constructor is browser-specific.
interface BrowserRecognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onstart: (() => void) | null;
  onaudioend: (() => void) | null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

type RecognitionWindow = Window & {
  SpeechRecognition?: new () => BrowserRecognition;
  webkitSpeechRecognition?: new () => BrowserRecognition;
};

const noSpeech = 'Nenhuma fala reconhecida. Tente novamente ou digite sua mensagem.';
const microphoneDenied = 'Acesso ao microfone bloqueado. Confira a permissão do navegador ou digite sua mensagem.';
const recognitionFailed = 'Não foi possível transcrever. Tente novamente ou digite sua mensagem.';
const recognitionErrors: Partial<Record<SpeechRecognitionErrorEvent['error'], string>> = {
  'no-speech': noSpeech,
  'not-allowed': microphoneDenied,
  'service-not-allowed': 'O serviço de ditado está bloqueado neste navegador. Digite sua mensagem.',
  'audio-capture': 'O microfone está indisponível. Confira o dispositivo ou digite sua mensagem.',
  'network': 'O serviço de ditado está indisponível. Confira a conexão ou digite sua mensagem.',
  'language-not-supported': 'O navegador não oferece ditado em português. Digite sua mensagem.',
};

@Component({
  selector: 'app-chat-voice-input',
  imports: [LucideMic, LucideSquare],
  templateUrl: './chat-voice-input.html',
  styleUrl: './chat-voice-input.css',
})
export class ChatVoiceInput {
  readonly disabled = input(false);
  readonly transcript = output<string>();

  private readonly browser = inject(DOCUMENT).defaultView as RecognitionWindow | null;
  private readonly recognitionType = this.browser?.SpeechRecognition ?? this.browser?.webkitSpeechRecognition;
  private readonly phase = signal<'idle' | 'starting' | 'listening' | 'finishing'>('idle');
  private readonly feedback = signal('');
  private recognition: BrowserRecognition | null = null;

  readonly isCapturing = computed(() => this.phase() !== 'idle');
  protected readonly recording = computed(() => this.phase() === 'starting' || this.phase() === 'listening');
  protected readonly buttonDisabled = computed(() => !this.recognitionType || this.disabled() || this.phase() === 'finishing');
  protected readonly buttonLabel = computed(() => this.recording() ? 'Parar ditado' : 'Ditar mensagem');
  protected readonly status = computed(() => {
    if (!this.recognitionType) return 'Ditado indisponível neste navegador.';
    if (this.feedback()) return this.feedback();
    switch (this.phase()) {
      case 'starting': return 'Ativando microfone…';
      case 'listening': return 'Ouvindo…';
      case 'finishing': return 'Concluindo transcrição…';
      default: return '';
    }
  });

  constructor() {
    effect(() => {
      if (this.disabled()) this.cancel();
    });
    inject(DestroyRef).onDestroy(() => this.cancel());
  }

  protected toggle(): void {
    if (this.buttonDisabled() || !this.recognitionType) return;

    try {
      if (this.recognition) {
        this.phase.set('finishing');
        this.recognition.stop();
      } else {
        this.start(new this.recognitionType());
      }
    } catch (error) {
      this.cancel();
      this.feedback.set(error instanceof DOMException && error.name === 'NotAllowedError'
        ? microphoneDenied
        : recognitionFailed);
    }
  }

  private start(recognition: BrowserRecognition): void {
    this.recognition = recognition;
    this.feedback.set('');
    this.phase.set('starting');
    recognition.lang = 'pt-BR';
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onstart = () => {
      if (this.recognition === recognition && this.phase() === 'starting') this.phase.set('listening');
    };
    recognition.onaudioend = () => {
      if (this.recognition === recognition) this.phase.set('finishing');
    };
    recognition.onresult = (event) => {
      if (this.recognition !== recognition || this.disabled()) return;
      const result = event.results[event.resultIndex];
      const text = result?.isFinal ? result[0]?.transcript.trim() : '';
      if (text) {
        this.feedback.set('Transcrição adicionada. Revise o texto antes de enviar.');
        this.transcript.emit(text);
      }
    };
    recognition.onerror = (event) => {
      if (this.recognition !== recognition) return;
      this.phase.set('finishing');
      this.feedback.set(recognitionErrors[event.error] ?? recognitionFailed);
    };
    recognition.onend = () => {
      if (this.recognition !== recognition) return;
      this.releaseRecognition();
      if (!this.feedback()) this.feedback.set(noSpeech);
    };
    recognition.start();
  }

  private cancel(): void {
    this.releaseRecognition()?.abort();
    this.feedback.set('');
  }

  private releaseRecognition(): BrowserRecognition | null {
    const recognition = this.recognition;
    this.recognition = null;
    this.phase.set('idle');
    if (recognition) {
      recognition.onstart = null;
      recognition.onaudioend = null;
      recognition.onresult = null;
      recognition.onerror = null;
      recognition.onend = null;
    }
    return recognition;
  }
}
