## 3.2.0 - Unreleased

## 3.1.0 - Released

The focus of this release was to introduce Batch Voucher Exports CRUD APIs and add business logic for integration during the approval of an invoice

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v3.0.1...v3.1.0)

### Stories
* [MODINVOICE-139](https://issues.folio.org/browse/MODINVOICE-139) - Create PUT /voucher/vouchers/<id>
* [MODINVOICE-130](https://issues.folio.org/browse/MODINVOICE-130) - Create batch-voucher API
* [MODINVOICE-129](https://issues.folio.org/browse/MODINVOICE-129) - Create credentials API
* [MODINVOICE-128](https://issues.folio.org/browse/MODINVOICE-128) - Create batch-voucher-configurations CRUD API
* [MODINVOICE-127](https://issues.folio.org/browse/MODINVOICE-127) - Create batch-group CRUD API
* [MODINVOICE-126](https://issues.folio.org/browse/MODINVOICE-126) - XML schema for batch-voucher
* [MODINVOICE-122](https://issues.folio.org/browse/MODINVOICE-122) - Investigate OOM issues on mod-invoice
* [MODINVOICE-117](https://issues.folio.org/browse/MODINVOICE-117) - Include invoice level Fund distributions in Voucher information summary
* [MODINVOICE-113](https://issues.folio.org/browse/MODINVOICE-113) - Call awaiting-payment API upon invoice approval

### Bug Fixes
* [MODINVOICE-147](https://issues.folio.org/browse/MODINVOICE-147) - Invoice approval fails if a voucherNumberPrefix isn't configured
* [MODINVOICE-118](https://issues.folio.org/browse/MODINVOICE-118) - mod-invoice was out-of-memory killed on folio-snapshot-demo system

## 3.0.1 - Released

The main focus of this bugfix release was to fix issue with voucher "accounting code"

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v3.0.0...v3.0.1)

### Bug Fixes
* [MODINVOICE-122](https://issues.folio.org/browse/MODINVOICE-122) - Investigate OOM issues on mod-invoice
* [MODINVOICE-120](https://issues.folio.org/browse/MODINVOICE-120) - Voucher "accounting code" not set

## 3.0.0 - Released

The main focus of this release was to re-integrate with finance module, fix bugs, calculations improvements

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v2.0.0...v3.0.0)

### Stories
* [MODINVOICE-116](https://issues.folio.org/browse/MODINVOICE-116) - Update purchase order number pattern
* [MODINVOICE-115](https://issues.folio.org/browse/MODINVOICE-115) - Use JVM features to manage container memory
* [MODINVOICE-99](https://issues.folio.org/browse/MODINVOICE-99) - Fund Distribution schema changes
* [MODINVOICE-89](https://issues.folio.org/browse/MODINVOICE-89) - Disallow "deleted" acq units from being assigned to invoices
* [MODINVOICE-84](https://issues.folio.org/browse/MODINVOICE-84) - use finance business logic module
* [MODINVOICE-61](https://issues.folio.org/browse/MODINVOICE-61) - Prorated adjustments part 3 - fractional amounts

### Bug Fixes
* [MODINVOICE-103](https://issues.folio.org/browse/MODINVOICE-103) - Adjustment Total Calculation is wrong
* [MODINVOICE-97](https://issues.folio.org/browse/MODINVOICE-97) - Can not pay the invoice
* [MODINVOICE-95](https://issues.folio.org/browse/MODINVOICE-95) - Once an invoice is Paid it should no longer transition to other statuses

## 2.0.0 - Released

The primary focus of this release was to implement Teams-based operations restriction logic and provide the APIs for managing invoice attachments.

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v1.0.0...v2.0.0)

### Stories
 * [MODINVOICE-96](https://issues.folio.org/browse/MODINVOICE-96) - Add support for latest and v3.0.0 mod-finance-storage versions
 * [MODINVOICE-91](https://issues.folio.org/browse/MODINVOICE-91) - Change usage of invoice settings
 * [MODINVOICE-90](https://issues.folio.org/browse/MODINVOICE-90) - Implement basic GET /voucher/voucher-lines
 * [MODINVOICE-87](https://issues.folio.org/browse/MODINVOICE-87) - Calculate and persist totals upon invoice creation/update/get
 * [MODINVOICE-86](https://issues.folio.org/browse/MODINVOICE-86) - Calculate and persist totals upon invoiceLine creation/update
 * [MODINVOICE-81](https://issues.folio.org/browse/MODINVOICE-81) - Implement API for invoice attachments (links/documents)
 * [MODINVOICE-77](https://issues.folio.org/browse/MODINVOICE-77) - Invoice and invoiceLine schema updates
 * [MODINVOICE-74](https://issues.folio.org/browse/MODINVOICE-74) - Transpose invoice acquisitions-units to voucher upon voucher creation
 * [MODINVOICE-72](https://issues.folio.org/browse/MODINVOICE-72) - Restrict search/view of invoice, invoiceLine records based upon acquisitions unit
 * [MODINVOICE-71](https://issues.folio.org/browse/MODINVOICE-71) - Restrict deletion of invoice, invoiceLine records based upon acquisitions unit
 * [MODINVOICE-70](https://issues.folio.org/browse/MODINVOICE-70) - Restrict updates of invoice, invoiceLine records based upon acquisitions unit
 * [MODINVOICE-69](https://issues.folio.org/browse/MODINVOICE-69) - Restrict creation of invoice, invoiceLine records based upon acquisitions unit
 * [MODINVOICE-54](https://issues.folio.org/browse/MODINVOICE-54) - Prorated adjustments part 1 - basics

## 1.0.0 - Released

The primary focus of this release was to implement backend logic to manage invoices, invoice lines, vouchers, voucher lines and provide backend logic for the transitioning invoice to Approved and Paid status.

### Stories
 * [MODINVOICE-79](https://issues.folio.org/browse/MODINVOICE-79) - Assign tags to Invoice Records
 * [MODINVOICE-78](https://issues.folio.org/browse/MODINVOICE-78) - Assign tags to Invoice Line Records
 * [MODINVOICE-76](https://issues.folio.org/browse/MODINVOICE-76) - Invoice approval details set by system
 * [MODINVOICE-75](https://issues.folio.org/browse/MODINVOICE-75) - Fund Distribution only required upon transition to approval
 * [MODINVOICE-67](https://issues.folio.org/browse/MODINVOICE-67) - Make product id type to be uuid
 * [MODINVOICE-60](https://issues.folio.org/browse/MODINVOICE-60) - Invoice schemas - readonly properties
 * [MODINVOICE-57](https://issues.folio.org/browse/MODINVOICE-57) - Implement GET /voucher/vouchers
 * [MODINVOICE-53](https://issues.folio.org/browse/MODINVOICE-53) - Adjustments schema updates
 * [MODINVOICE-52](https://issues.folio.org/browse/MODINVOICE-52) - Calculate Invoice Totals
 * [MODINVOICE-50](https://issues.folio.org/browse/MODINVOICE-50) - Calculate invoiceLine Totals
 * [MODINVOICE-49](https://issues.folio.org/browse/MODINVOICE-49) - Voucher/VoucherLine schema updates
 * [MODINVOICE-48](https://issues.folio.org/browse/MODINVOICE-48) - Implement POST /voucher/voucher-number/start/<val>
 * [MODINVOICE-47](https://issues.folio.org/browse/MODINVOICE-47) - Implement GET /voucher/voucher-number/start
 * [MODINVOICE-46](https://issues.folio.org/browse/MODINVOICE-46) - voucherNumber Generation
 * [MODINVOICE-42](https://issues.folio.org/browse/MODINVOICE-42) - Invoice/Invoice-line schema updates
 * [MODINVOICE-41](https://issues.folio.org/browse/MODINVOICE-41) - Implement basic GET /voucher/vouchers/<id>
 * [MODINVOICE-40](https://issues.folio.org/browse/MODINVOICE-40) - implement basic PUT for /voucher/voucher-lines/<id>
 * [MODINVOICE-39](https://issues.folio.org/browse/MODINVOICE-39) - Implement basic GET /voucher/voucher-lines/<id>
 * [MODINVOICE-37](https://issues.folio.org/browse/MODINVOICE-37) - Update POL paymentStatus on transition of invoice Status
 * [MODINVOICE-36](https://issues.folio.org/browse/MODINVOICE-36) - Mark voucher as paid on transition to invoice status "Paid"
 * [MODINVOICE-35](https://issues.folio.org/browse/MODINVOICE-35) - Prevent modifications to invoice/invoice lines once status is "Approved"
 * [MODINVOICE-34](https://issues.folio.org/browse/MODINVOICE-34) - Create voucher on transition to "Approved"
 * [MODINVOICE-33](https://issues.folio.org/browse/MODINVOICE-33) - Implement DELETE /invoice/invoices/id
 * [MODINVOICE-32](https://issues.folio.org/browse/MODINVOICE-32) - Implement DELETE /invoice/invoice-lines/id
 * [MODINVOICE-31](https://issues.folio.org/browse/MODINVOICE-31) - Implement basic PUT /invoice/invoices/id
 * [MODINVOICE-30](https://issues.folio.org/browse/MODINVOICE-30) - Implement basic PUT /invoice/invoice-lines/id
 * [MODINVOICE-29](https://issues.folio.org/browse/MODINVOICE-29) - Implement GET /invoice/invoice-lines/id
 * [MODINVOICE-28](https://issues.folio.org/browse/MODINVOICE-28) - Implement GET /invoice/invoices/id
 * [MODINVOICE-25](https://issues.folio.org/browse/MODINVOICE-25) - Use Json API for loading module schemas
 * [MODINVOICE-23](https://issues.folio.org/browse/MODINVOICE-23) - Implement GET /invoicing/invoice-lines
 * [MODINVOICE-22](https://issues.folio.org/browse/MODINVOICE-22) - Implement GET /invoice/invoices
 * [MODINVOICE-21](https://issues.folio.org/browse/MODINVOICE-21) - Implement POST /invoicing/invoice-lines
 * [MODINVOICE-20](https://issues.folio.org/browse/MODINVOICE-20) - Implement POST /invoicing/invoices
 * [MODINVOICE-17](https://issues.folio.org/browse/MODINVOICE-17) - Define API
 * [MODINVOICE-16](https://issues.folio.org/browse/MODINVOICE-16) - Project Setup
 * [MODINVOICE-14](https://issues.folio.org/browse/MODINVOICE-14) - Invoice: "Vendor Invoice Number" field validation
 
### Bug Fixes
 * [MODINVOICE-66](https://issues.folio.org/browse/MODINVOICE-66) - Shouldn't be allowed to add invoiceLines to an "approved" or "paid" invoice
