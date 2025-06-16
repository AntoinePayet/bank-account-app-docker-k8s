import {ChangeDetectorRef, Component, OnInit} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-customers',
  imports: [],
  templateUrl: './customers.html',
  styleUrl: './customers.css'
})
export class Customers implements OnInit {
  customers: any;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.http.get('http://localhost:8888/CUSTOMER-SERVICE/customers')
      .subscribe({
        next: data => {
          this.customers = data;
          this.cdr.detectChanges();
        },
        error: err => {
          console.error(err);
        }
      })
  }
}
