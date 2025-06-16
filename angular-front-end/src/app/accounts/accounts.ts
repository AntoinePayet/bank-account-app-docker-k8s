import {ChangeDetectorRef, Component, OnInit} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-accounts',
  imports: [],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class Accounts implements OnInit {
  accounts: any;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.http.get('http://localhost:8888/ACCOUNT-SERVICE/accounts')
      .subscribe({
        next: data => {
          this.accounts = data;
          this.cdr.detectChanges();
        },
        error: err => {
          console.error(err);
        }
      })
  }
}
