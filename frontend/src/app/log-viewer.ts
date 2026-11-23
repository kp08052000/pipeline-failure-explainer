import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, input, viewChild } from '@angular/core';

@Component({
  selector: 'app-log-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="viewer" #viewer role="region" aria-label="Analysed log with evidence highlighted" tabindex="0">
      @for (line of lines(); track $index) {
        <div
          class="line"
          [id]="'log-line-' + ($index + 1)"
          [class.evidence]="evidenceSet().has($index + 1)"
          [class.selected]="selectedLine() === $index + 1"
        >
          <span class="no">{{ $index + 1 }}</span><span class="text">{{ line || ' ' }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    .viewer {
      position: relative;
      height: 100%;
      min-height: 360px;
      max-height: calc(100vh - 260px);
      overflow: auto;
      background: var(--code-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      font: 12.5px/1.55 var(--mono);
      padding: 6px 0;
    }
    .line { display: flex; white-space: pre; padding: 0 12px 0 0; }
    .no {
      flex: 0 0 3.5em;
      text-align: right;
      padding-right: 12px;
      color: var(--muted);
      user-select: none;
    }
    .evidence { background: var(--evidence-bg); }
    .evidence .no { color: var(--evidence-fg); font-weight: 600; }
    .selected { background: var(--selected-bg); outline: 1px solid var(--evidence-fg); }
  `,
})
export class LogViewer {
  readonly lines = input.required<string[]>();
  readonly evidenceLines = input<number[]>([]);
  readonly selectedLine = input<number | null>(null);

  protected readonly evidenceSet = computed(() => new Set(this.evidenceLines()));
  private readonly viewer = viewChild.required<ElementRef<HTMLElement>>('viewer');

  constructor() {
    afterRenderEffect(() => {
      const line = this.selectedLine();
      if (line === null) {
        return;
      }
      const viewer = this.viewer().nativeElement;
      const el = viewer.querySelector<HTMLElement>(`#log-line-${line}`);
      if (el) {
        viewer.scrollTo({ top: el.offsetTop - viewer.clientHeight / 2, behavior: 'smooth' });
      }
    });
  }
}
