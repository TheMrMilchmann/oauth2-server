import {Gw2ApiPermission} from './general.model';

export interface Token {
    gw2AccountId: string;
    creationTime: Date;
    gw2ApiToken: string;
    displayName: string;
    gw2ApiPermissions: Gw2ApiPermission[];
    isVerified: boolean;
}