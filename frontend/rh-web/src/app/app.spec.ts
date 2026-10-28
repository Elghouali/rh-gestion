import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController,provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { LanguageService } from './core/language.service';
describe('RH workspace',()=>{
 let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({imports:[App],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
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
});