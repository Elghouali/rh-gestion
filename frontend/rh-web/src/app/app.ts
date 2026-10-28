import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { LanguageService } from './core/language.service';
interface SessionInfo {
  authenticated: boolean;
  displayName: string;
  roles: string[];
}
interface CsrfInfo {
  parameterName: string;
  headerName: string;
  token: string;
}
interface Branch {
  id: string;
  code: string;
  name: string;
  type: 'DEPOT' | 'STORE';
}
@Component({ selector: 'app-root', imports: [], templateUrl: './app.html', styleUrl: './app.scss' })
export class App implements OnInit {
  readonly lang = inject(LanguageService);
  private readonly http = inject(HttpClient);
  readonly denied = signal(false);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly session = signal<SessionInfo>({ authenticated: false, displayName: '', roles: [] });
  readonly csrf = signal<CsrfInfo | null>(null);
  readonly branches = signal<Branch[]>([]);
  readonly view = signal<'overview' | 'branches'>('overview');
  ngOnInit() {
    this.load();
  }
  load() {
    this.state.set('loading');
    this.branches.set([]);
    this.denied.set(false);
    forkJoin({
      csrf: this.http.get<CsrfInfo>('/api/v1/csrf'),
      session: this.http.get<SessionInfo>('/api/v1/session'),
    }).subscribe({
      next: ({ csrf, session }) => {
        this.csrf.set(csrf);
        this.session.set(session);
        if (!session.authenticated) {
          this.state.set('ready');
          return;
        }
        this.http.get<Branch[]>('/api/v1/branches').subscribe({
          next: (branches) => {
            this.branches.set(branches);
            this.state.set('ready');
          },
          error: (error: HttpErrorResponse) => {
            if (error.status === 401) {
              this.session.set({ authenticated: false, displayName: '', roles: [] });
              this.state.set('ready');
              return;
            }
            this.denied.set(error.status === 403);
            this.state.set('error');
          },
        });
      },
      error: () => {
        this.csrf.set(null);
        this.session.set({ authenticated: false, displayName: '', roles: [] });
        this.state.set('error');
      },
    });
  }
  print() {
    window.print();
  }
}
