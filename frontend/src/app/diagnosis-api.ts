import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { Diagnosis } from './diagnosis.model';

@Injectable({ providedIn: 'root' })
export class DiagnosisApi {
  private readonly http = inject(HttpClient);

  diagnose(log: string): Observable<Diagnosis> {
    return this.http
      .post<Diagnosis>('/api/diagnose', { log })
      .pipe(catchError((err: HttpErrorResponse) => throwError(() => new Error(toMessage(err)))));
  }

  provider(): Observable<string> {
    return this.http.get<{ provider: string }>('/api/status').pipe(map((s) => s.provider));
  }
}

function toMessage(err: HttpErrorResponse): string {
  if (err.status === 0) {
    return 'Cannot reach the backend. Is it running on port 8080?';
  }
  const message = (err.error as { message?: unknown } | null)?.message;
  return typeof message === 'string' ? message : `Request failed (HTTP ${err.status}).`;
}
