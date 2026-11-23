export type Verdict = 'DIAGNOSED' | 'INSUFFICIENT_EVIDENCE';
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type Risk = 'SAFE' | 'CAUTION';

export interface Evidence {
  line: number;
  text: string;
}

export interface Cause {
  title: string;
  explanation: string;
  confidence: Confidence;
  evidence: Evidence[];
}

export interface Suggestion {
  action: string;
  command: string | null;
  rationale: string;
  risk: Risk;
  riskNote: string | null;
}

export interface Diagnosis {
  verdict: Verdict;
  summary: string;
  failedStep: string | null;
  category: string;
  causes: Cause[];
  checks: Suggestion[];
  fixes: Suggestion[];
  missingInformation: string[];
  warnings: string[];
  provider: string;
  lineCount: number;
  redactionCount: number;
}
