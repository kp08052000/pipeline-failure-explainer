import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { DiagnosisApi } from './diagnosis-api';
import { Diagnosis } from './diagnosis.model';
import { DiagnosisReport } from './diagnosis-report';
import { LogViewer } from './log-viewer';
import { SAMPLE_LOGS, SampleLog } from './sample-logs';

const MAX_LOG_CHARS = 100_000;

@Component({
  selector: 'app-root',
  imports: [DiagnosisReport, LogViewer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly api = inject(DiagnosisApi);
  private readonly destroyRef = inject(DestroyRef);
  private pending?: Subscription;

  protected readonly samples = SAMPLE_LOGS;
  protected readonly maxChars = MAX_LOG_CHARS;

  protected readonly log = signal('');
  protected readonly activeSample = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly diagnosis = signal<Diagnosis | null>(null);
  protected readonly analysedLines = signal<string[]>([]);
  protected readonly view = signal<'input' | 'evidence'>('input');
  protected readonly selectedLine = signal<number | null>(null);
  protected readonly provider = signal<string | null>(null);

  protected readonly lineCount = computed(() => (this.log() ? splitLines(this.log()).length : 0));
  protected readonly tooLong = computed(() => this.log().length > MAX_LOG_CHARS);
  protected readonly canAnalyse = computed(() => !this.loading() && this.log().trim().length > 0 && !this.tooLong());
  protected readonly evidenceLines = computed(
    () => this.diagnosis()?.causes.flatMap((c) => c.evidence.map((e) => e.line)) ?? [],
  );

  constructor() {
    this.api
      .provider()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (p) => this.provider.set(p), error: () => this.provider.set(null) });
  }

  protected loadSample(sample: SampleLog): void {
    this.activeSample.set(sample.id);
    this.setLog(sample.log);
  }

  protected onInput(event: Event): void {
    this.activeSample.set(null);
    this.setLog((event.target as HTMLTextAreaElement).value);
  }

  protected clear(): void {
    this.activeSample.set(null);
    this.setLog('');
  }

  protected analyse(): void {
    if (!this.canAnalyse()) {
      return;
    }
    const log = this.log();
    this.pending?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.diagnosis.set(null);
    this.selectedLine.set(null);

    this.pending = this.api
      .diagnose(log)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (d) => {
          this.diagnosis.set(d);
          this.analysedLines.set(splitLines(log));
          this.view.set('evidence');
          this.loading.set(false);
        },
        error: (e: Error) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  protected selectEvidence(line: number): void {
    this.view.set('evidence');
    this.selectedLine.set(line);
  }

  private setLog(value: string): void {
    this.pending?.unsubscribe();
    this.loading.set(false);
    this.log.set(value);
    this.diagnosis.set(null);
    this.error.set(null);
    this.selectedLine.set(null);
    this.view.set('input');
  }
}

// Must match backend line numbering (CRLF/CR normalised to LF).
function splitLines(text: string): string[] {
  return text.split(/\r\n|\r|\n/);
}
