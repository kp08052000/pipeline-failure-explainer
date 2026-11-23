import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { Diagnosis } from './diagnosis.model';

const INSUFFICIENT: Diagnosis = {
  verdict: 'INSUFFICIENT_EVIDENCE',
  summary: 'The step failed but the log has no underlying error.',
  failedStep: 'Build',
  category: 'unknown',
  causes: [],
  checks: [],
  fixes: [],
  missingInformation: ['Full output of the failing step'],
  warnings: [],
  provider: 'offline-rules',
  lineCount: 2,
  redactionCount: 0,
};

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('loads a sample and renders an insufficient-evidence diagnosis', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectOne('/api/status').flush({ provider: 'offline-rules' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    const sampleButton = Array.from(el.querySelectorAll<HTMLButtonElement>('button.sample')).find((b) =>
      b.textContent?.includes('insufficient'),
    );
    sampleButton!.click();
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('button.primary')!.click();

    const req = http.expectOne('/api/diagnose');
    expect(req.request.body.log).toContain("Bash exited with code '2'");
    req.flush(INSUFFICIENT);
    await fixture.whenStable();

    expect(el.textContent).toContain('Insufficient evidence');
    expect(el.textContent).toContain('Full output of the failing step');
    http.verify();
  });
});
