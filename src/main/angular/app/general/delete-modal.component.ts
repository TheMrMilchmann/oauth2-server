import {Component, Input} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {faTrashAlt, faTimes} from '@fortawesome/free-solid-svg-icons';


@Component({
    selector: 'app-delete-modal',
    template: `
        <div class="modal-header">
            <h5 class="modal-title">Delete {{entityName}}</h5>
            <button type="button" class="btn btn-sm" (click)="dismiss()" aria-label="Close"><fa-icon [icon]="faTimes"></fa-icon></button>
        </div>
        <div class="modal-body">
            <p>Do you really want to delete the {{entityType}} <strong>{{entityName}}</strong>?</p>

            <form>
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" value="" [id]="'delete-modal-confirm'" [(ngModel)]="confirmed" name="confirmed" />
                    <label [htmlFor]="'delete-modal-confirm'">Yes, permanently delete</label>
                </div>
            </form>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-accent" (click)="dismiss()"><fa-icon [icon]="faTimes"></fa-icon> Cancel</button>
            <button type="button" class="btn btn-danger" [disabled]="!confirmed" (click)="close()"><fa-icon [icon]="faTrashAlt"></fa-icon> Delete</button>
        </div>
    `
})
export class DeleteModalComponent {

    faTrashAlt = faTrashAlt;
    faTimes = faTimes;

    @Input('entityType') entityType!: string;
    @Input('entityName') entityName!: string;

    confirmed = false;

    constructor(private readonly activeModal: NgbActiveModal) {}

    dismiss(): void {
        this.activeModal.dismiss(false);
    }

    close(): void {
        this.activeModal.close(this.confirmed);
    }
}