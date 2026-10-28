import {vi} from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController,provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { LanguageService } from './core/language.service';
describe('RH workspace',()=>{
 let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({imports:[App],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);TestBed.inject(LanguageService).set('fr');});
 afterEach(()=>http.verify());
 function render(authenticated:boolean){
  const fixture=TestBed.createComponent(App);fixture.detectChanges();
  http.expectOne('/api/v1/csrf').flush({parameterName:'_csrf',headerName:'X-XSRF-TOKEN',token:'test-token'});
  http.expectOne('/api/v1/session').flush({authenticated,displayName:authenticated?'Rachid Hanaoui':'',roles:authenticated?['OWNER']:[]});
  return fixture;
 }
 it('offers secure sign-in without inventing sales data',()=>{
  const f=render(false);f.detectChanges();
  expect(f.nativeElement.querySelector('a[href="/oauth2/authorization/rh"]')).not.toBeNull();
  expect(f.nativeElement.textContent).not.toContain('0 DH');
 });
 it('displays only the establishments returned by the server',()=>{
  const f=render(true);
  http.expectOne('/api/v1/branches').flush([{id:'depot',code:'DEPOT',name:'Dépôt central',type:'DEPOT'}]);f.detectChanges();
  expect(f.nativeElement.textContent).toContain('Dépôt central');
  expect(f.nativeElement.textContent).not.toContain('Magasin 4');
 });
 it('shows an explicit retry when the backend is unavailable',()=>{
  const f=TestBed.createComponent(App);f.detectChanges();
  http.expectOne('/api/v1/csrf').flush({parameterName:'_csrf',headerName:'X-XSRF-TOKEN',token:'token'});
  http.expectOne('/api/v1/session').flush('Unavailable',{status:503,statusText:'Unavailable'});f.detectChanges();
  expect(f.nativeElement.querySelector('[data-testid="retry"]')).not.toBeNull();
  expect(f.nativeElement.querySelector('form[action="/logout"]')).toBeNull();
 });
 it('switches the document direction and interface to Arabic',()=>{
  const f=render(false);TestBed.inject(LanguageService).set('ar');f.detectChanges();
  expect(document.documentElement.dir).toBe('rtl');
  expect(f.nativeElement.textContent).toContain('تسجيل الدخول');
 });
 it('shows the user name and CSRF-protected logout form',()=>{
  const f=render(true);http.expectOne('/api/v1/branches').flush([]);f.detectChanges();
  expect(f.nativeElement.textContent).toContain('Rachid Hanaoui');
  expect(f.nativeElement.querySelector('input[name="_csrf"]').value).toBe('test-token');
 });

 it('keeps logout available when branch access is forbidden',()=>{
  const f=render(true);http.expectOne('/api/v1/branches').flush('Denied',{status:403,statusText:'Forbidden'});f.detectChanges();
  expect(f.nativeElement.querySelector('form[action="/logout"]')).not.toBeNull();
 });
 it('offers sign-in again when the backend session expires',()=>{
  const f=render(true);http.expectOne('/api/v1/branches').flush('Expired',{status:401,statusText:'Unauthorized'});f.detectChanges();
  expect(f.nativeElement.querySelector('a[href="/oauth2/authorization/rh"]')).not.toBeNull();
 });


 it('switches all language controls without reloading the page',()=>{
  const f=render(false);f.detectChanges();
  f.nativeElement.querySelector('[aria-label="English"]').click();f.detectChanges();
  expect(document.documentElement.lang).toBe('en');expect(f.nativeElement.textContent).toContain('Sign in');
  f.nativeElement.querySelector('[aria-label="العربية"]').click();f.detectChanges();
  expect(document.documentElement.dir).toBe('rtl');
  f.nativeElement.querySelector('[aria-label="Français"]').click();f.detectChanges();
  expect(document.documentElement.dir).toBe('ltr');expect(f.nativeElement.textContent).toContain('Se connecter');
 });
 it('navigates between overview and establishments',()=>{
  const f=render(true);http.expectOne('/api/v1/branches').flush([]);f.detectChanges();
  f.nativeElement.querySelectorAll('nav button')[1].click();f.detectChanges();
  expect(f.nativeElement.querySelector('h1').textContent).toContain('Établissements');
  f.nativeElement.querySelectorAll('nav button')[0].click();f.detectChanges();
  expect(f.nativeElement.querySelector('h1').textContent).toContain('Vue d’ensemble');
 });
 it('retries a failed bootstrap and restores sign-in',()=>{
  const f=TestBed.createComponent(App);f.detectChanges();
  http.expectOne('/api/v1/csrf').flush({parameterName:'_csrf',headerName:'X-XSRF-TOKEN',token:'one'});
  http.expectOne('/api/v1/session').flush('Unavailable',{status:503,statusText:'Unavailable'});f.detectChanges();
  f.nativeElement.querySelector('[data-testid="retry"]').click();f.detectChanges();
  http.expectOne('/api/v1/csrf').flush({parameterName:'_csrf',headerName:'X-XSRF-TOKEN',token:'two'});
  http.expectOne('/api/v1/session').flush({authenticated:false,displayName:'',roles:[]});f.detectChanges();
  expect(f.nativeElement.querySelector('a[href="/oauth2/authorization/rh"]')).not.toBeNull();
 });
 it('opens the browser print action for the locations sheet',()=>{
  const print=vi.spyOn(window,'print').mockImplementation(()=>{});
  const f=render(true);http.expectOne('/api/v1/branches').flush([]);f.detectChanges();
  f.nativeElement.querySelector('.print-button').click();expect(print).toHaveBeenCalledOnce();print.mockRestore();
 });

});