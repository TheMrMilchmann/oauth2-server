import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {Gw2TokenInfo} from './gw2-api.model';

@Injectable({
    providedIn: 'root'
})
export class Gw2ApiService {

    constructor(private readonly httpClient: HttpClient) {
    }

    getTokenInfo(token: string): Observable<Gw2TokenInfo> {
        return this.httpClient.get<Gw2TokenInfo>('https://api.guildwars2.com/v2/tokeninfo', { params: { 'access_token': token } });
    }
}