import {Component, OnInit} from '@angular/core';
import {VerificationService} from './verification.service';
import {VerificationChallengeStart} from './verification.model';
import {TokenService} from '../../../common/token.service';
import {Token} from '../../../common/token.model';
import {firstValueFrom} from 'rxjs';
import {Router} from '@angular/router';
import {ToastService} from '../../../toast/toast.service';
import {Gw2ApiPermission} from '../../../common/common.model';
import {Gw2ApiService} from '../../../common/gw2-api.service';
import {faCheck} from '@fortawesome/free-solid-svg-icons';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';


@Component({
    selector: 'app-verification-setup-submit',
    templateUrl: './verification-setup-submit.component.html'
})
export class VerificationSetupSubmitComponent implements OnInit {

    faCheck = faCheck;

    startedChallenge: VerificationChallengeStart | null = null;
    requiredGw2ApiPermissions: Gw2ApiPermission[] = [];
    tokens: Token[] = [];

    verificationNewApiToken = '';
    verificationApiTokenInUse = '';
    verificationTokenType = '- Not yet selected -';
    verificationTokenName = '- Not yet selected -';
    verificationTokenGw2ApiPermissions: Gw2ApiPermission[] = [];
    verificationTokenCheckAvailable = false;
    tokenCheckInProgress = false;
    tokenCheckCheckedToken: string | null = null;
    submitInProgress = false;

    constructor(private readonly verificationService: VerificationService,
                private readonly tokenService: TokenService,
                private readonly gw2ApiService: Gw2ApiService,
                private readonly toastService: ToastService,
                private readonly router: Router,
                private readonly sanitizer: DomSanitizer) {
    }

    ngOnInit(): void {
        const startedChallengePromise = firstValueFrom(this.verificationService.getStartedChallenge());
        const availableChallengesPromise = firstValueFrom(this.verificationService.getAvailableChallenges());
        const tokensPromise = firstValueFrom(this.tokenService.getTokens());

        Promise.all([startedChallengePromise, availableChallengesPromise, tokensPromise])
            .then((values) => {
                const [startedChallenge, availableChallenges, tokens] = values;
                const requiredGw2ApiPermissions: Gw2ApiPermission[] = [];

                for (let challenge of availableChallenges) {
                    if (challenge.id == startedChallenge.challengeId) {
                        requiredGw2ApiPermissions.push(...challenge.requiredGw2ApiPermissions);
                        break;
                    }
                }

                this.tokens = tokens
                    .filter((token) => !token.isVerified)
                    .filter((token) => {
                        for (let requiredGw2ApiPermission of requiredGw2ApiPermissions) {
                            if (!token.gw2ApiPermissions.includes(requiredGw2ApiPermission)) {
                                return false;
                            }
                        }

                        return true;
                    });

                this.requiredGw2ApiPermissions = requiredGw2ApiPermissions;
                this.startedChallenge = startedChallenge;
            })
            .catch((e) => {
                this.toastService.show('Failed to retrieve data', 'The data could not be loaded. Please try again later: ' + e);
            });
    }

    getChallengeName(id: number): string {
        switch (id) {
            case 1: return 'API-Token Name';
            case 2: return 'TP Buy-Order';
            default: return 'Unknown';
        }
    }

    getYoutubeEmbedSrc(challengeId: number, type: 'new' | 'existing'): SafeResourceUrl {
        return this.sanitizer.bypassSecurityTrustResourceUrl(this.__getYoutubeEmbedSrc(challengeId, type));
    }

    __getYoutubeEmbedSrc(challengeId: number, type: 'new' | 'existing'): string {
        switch (challengeId) {
            case 1: return 'https://www.youtube.com/embed/xgaG9ysH3is';
            case 2: {
                switch (type) {
                    case 'new': return 'https://www.youtube.com/embed/Lt50s84D2b4';
                    case 'existing': return 'https://www.youtube.com/embed/W1Gu4kCLx0g';
                }
            }
        }

        throw Error();
    }

    onNewTokenChange(newApiToken: string): void {
        this.verificationApiTokenInUse = newApiToken;
        this.verificationTokenType = 'New';
        this.verificationTokenName = '- Not yet checked -';
        this.verificationTokenGw2ApiPermissions = [];
        this.verificationTokenCheckAvailable = true;
        this.tokenCheckCheckedToken = null;
    }

    onExistingTokenClick(token: Token): void {
        this.verificationNewApiToken = '';
        this.verificationApiTokenInUse = token.gw2ApiToken;
        this.verificationTokenType = 'Existing';
        this.verificationTokenName = token.displayName;
        this.verificationTokenGw2ApiPermissions = token.gw2ApiPermissions;
        this.verificationTokenCheckAvailable = false;
        this.tokenCheckCheckedToken = token.gw2ApiToken;
    }

    onTokenCheckClick(): void {
        const tokenToCheck = this.verificationApiTokenInUse;
        this.tokenCheckInProgress = true;

        const tokenInfoPromise = firstValueFrom(this.gw2ApiService.getTokenInfo(tokenToCheck));
        const accountPromise = firstValueFrom(this.gw2ApiService.getAccount(tokenToCheck));

        Promise.all([tokenInfoPromise, accountPromise])
            .then((results) => {
                const [tokenInfo, account] = results;

                // only set these values if the selection didnt change in the meantime
                if (tokenToCheck == this.verificationApiTokenInUse) {
                    this.verificationTokenName = account.name;
                    this.verificationTokenGw2ApiPermissions = tokenInfo.permissions;

                    let hasAllRequiredGw2ApiPermissions = true;

                    for (let gw2ApiPermission of this.requiredGw2ApiPermissions) {
                        if (!tokenInfo.permissions.includes(gw2ApiPermission)) {
                            hasAllRequiredGw2ApiPermissions = false;
                            break;
                        }
                    }

                    if (hasAllRequiredGw2ApiPermissions) {
                        this.tokenCheckCheckedToken = tokenToCheck;
                    }
                }
            })
            .catch((e) => {
                this.toastService.show('Token check failed', 'Failed to perform token check: ' + e?.text);
            })
            .finally(() => {
                this.tokenCheckInProgress = false;
            });
    }

    onBackToInstructionsClick(): void {
        this.router.navigate(['/', 'account', 'verification', 'setup', 'instructions']);
    }

    onChallengeSubmitClick(): void {
        this.submitInProgress = true;

        firstValueFrom(this.verificationService.submitChallenge(this.verificationApiTokenInUse))
            .then((result) => {
                if (result.isSuccess) {
                    this.toastService.show('Challenge succeeded', 'The challenge was successfully submitted and succeeded. Your account is now verified.', false);
                } else {
                    this.toastService.show('Challenge submitted', 'The challenge was successfully submitted and will be checked in the background.', false);
                }

                this.router.navigate(['/', 'account', 'verification']);
            })
            .catch((e) => {
                let errMsg: string;

                console.log(e);

                if (e?.message) {
                    errMsg = e.message;
                } else {
                    errMsg = JSON.stringify(e);
                }

                this.toastService.show('Submit failed', 'Failed to submit verification challenge: ' + errMsg);
            })
            .finally(() => {
                this.submitInProgress = false;
            });
    }
}