import { TestBed } from '@angular/core/testing';
import { ChatVoiceInput } from './chat-voice-input';

class RecognitionStub {
  static current: RecognitionStub;
  lang = '';
  onstart: (() => void) | null = null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null = null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn(() => this.onstart?.());
  stop = vi.fn();
  abort = vi.fn();

  constructor() {
    RecognitionStub.current = this;
  }
}

function recognitionResult(text: string, isFinal = true): SpeechRecognitionEvent {
  return {
    resultIndex: 0,
    results: [Object.assign([{ transcript: text }], { isFinal })],
  } as unknown as SpeechRecognitionEvent;
}

describe('ChatVoiceInput', () => {
  beforeEach(() => {
    vi.stubGlobal('SpeechRecognition', undefined);
    vi.stubGlobal('webkitSpeechRecognition', RecognitionStub);
  });
  afterEach(() => vi.unstubAllGlobals());

  function renderVoiceInput() {
    const fixture = TestBed.createComponent(ChatVoiceInput);
    const transcript = vi.fn();
    fixture.componentInstance.transcript.subscribe(transcript);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const button = host.querySelector('button')!;
    return { fixture, button, transcript, status: host.querySelector('[role="status"]')! };
  }

  it('requests Brazilian Portuguese and returns the final text when dictation stops', () => {
    const { fixture, button, transcript, status } = renderVoiceInput();
    expect(button.type).toBe('button');
    button.click();
    fixture.detectChanges();
    const recognition = RecognitionStub.current;
    expect(recognition.lang).toBe('pt-BR');
    expect(button.getAttribute('aria-label')).toBe('Parar ditado');
    expect(status.textContent).toContain('Ouvindo');

    recognition.onresult?.(recognitionResult('trecho provisório', false));
    expect(transcript).not.toHaveBeenCalled();
    button.click();
    expect(recognition.stop).toHaveBeenCalledOnce();
    recognition.onresult?.(recognitionResult(' Almoço por quarenta reais '));
    recognition.onend?.();
    fixture.detectChanges();

    expect(transcript).toHaveBeenCalledExactlyOnceWith('Almoço por quarenta reais');
    expect(status.textContent).toContain('Revise o texto');
    expect(button.disabled).toBe(false);
  });

  it('explains a denied microphone permission and allows retrying', () => {
    const { fixture, button, transcript, status } = renderVoiceInput();
    button.click();
    const recognition = RecognitionStub.current;
    recognition.onerror?.({ error: 'not-allowed' } as SpeechRecognitionErrorEvent);
    recognition.onend?.();
    fixture.detectChanges();

    expect(status.textContent).toContain('Acesso ao microfone bloqueado');
    expect(transcript).not.toHaveBeenCalled();
    expect(button.disabled).toBe(false);
  });

  it('shows a fallback when the browser has no recognition API', () => {
    vi.stubGlobal('webkitSpeechRecognition', undefined);
    const { button, status } = renderVoiceInput();
    expect(button.disabled).toBe(true);
    expect(status.textContent).toContain('Ditado indisponível');
  });

  it('aborts on disable or destruction and ignores a late result', () => {
    const { fixture, button, transcript } = renderVoiceInput();
    button.click();
    const firstRecognition = RecognitionStub.current;
    const lateResult = firstRecognition.onresult;

    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(firstRecognition.abort).toHaveBeenCalledOnce();
    expect(button.disabled).toBe(true);

    fixture.componentRef.setInput('disabled', false);
    fixture.detectChanges();
    button.click();
    lateResult?.(recognitionResult('Ditado cancelado'));
    expect(transcript).not.toHaveBeenCalled();

    const activeRecognition = RecognitionStub.current;
    fixture.destroy();
    expect(activeRecognition.abort).toHaveBeenCalledOnce();
  });
});
