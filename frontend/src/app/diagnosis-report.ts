import { LowerCasePipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { Diagnosis, Suggestion } from './diagnosis.model';

@Component({
  selector: 'app-diagnosis-report',
  imports: [LowerCasePipe, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './diagnosis-report.html',
  styleUrl: './diagnosis-report.css',
})
export class DiagnosisReport {
  readonly diagnosis = input.required<Diagnosis>();
  readonly selectedLine = input<number | null>(null);
  readonly evidenceSelected = output<number>();

  protected readonly copied = signal<string | null>(null);

  protected async copy(suggestion: Suggestion): Promise<void> {
    if (!suggestion.command) {
      return;
    }
    try {
      await navigator.clipboard.writeText(suggestion.command);
      this.copied.set(suggestion.command);
      setTimeout(() => this.copied.set(null), 1500);
    } catch {
      this.copied.set(null);
    }
  }
}
