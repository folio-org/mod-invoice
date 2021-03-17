## 5.1.0 - Unreleased

## 5.0.0 - Released

The focus of this release was to update RMB, major changes in the display of lock total now this is a number. 
Also integration with EDIFACT format was done. 

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.1.3...v5.0.0)

### Technical tasks
* [MODINVOICE-219](https://issues.folio.org/browse/MODINVOICE-219) - Add personal data disclosure form
* [MODINVOICE-218](https://issues.folio.org/browse/MODINVOICE-218) - mod-invoice: Update RMB

### Stories
* [MODINVOICE-235](https://issues.folio.org/browse/MODINVOICE-235) - Populate "enclosureNeeded" and "accountNo" in the voucher from invoice
* [MODINVOICE-231](https://issues.folio.org/browse/MODINVOICE-231) - Logic for Invoice line data from Purchase Order Line
* [MODINVOICE-227](https://issues.folio.org/browse/MODINVOICE-227) - Implement action profile handler for invoice creation
* [MODINVOICE-224](https://issues.folio.org/browse/MODINVOICE-224) - Do not update payment status of Ongoing poLines
* [MODINVOICE-223](https://issues.folio.org/browse/MODINVOICE-223) - Populate paymentDate when invoice is paid
* [MODINVOICE-222](https://issues.folio.org/browse/MODINVOICE-222) - Ensure that Allowable Encumbrance and Allowable Expenditure restrictions are based on "Total Funding"
* [MODINVOICE-214](https://issues.folio.org/browse/MODINVOICE-214) - Support ability to display lock and calculated totals
* [MODINVOICE-213](https://issues.folio.org/browse/MODINVOICE-213) - Lock total must equal Calculated total to approve invoice
* [MODINVOICE-135](https://issues.folio.org/browse/MODINVOICE-135) - Remove invoice number from the invoice line number

### Bug Fixes
* [MODINVOICE-239](https://issues.folio.org/browse/MODINVOICE-239) - Voucher amount incorrect
* [MODINVOICE-217](https://issues.folio.org/browse/MODINVOICE-217) - Folio testing build fails due to incompatible invoice-storage.invoices dependency

## 4.1.3 - Released
The primary focus of this release was to fix splitting funds in voucher line, where funds with identical external account numbers

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.1.2...v4.1.3)

  ### Bug Fixes
 * [MODINVOICE-239](https://issues.folio.org/browse/MODINVOICE-239) - Voucher amount incorrect
 
## 4.1.2 - Released
The primary focus of this release was to fix Adding certain prefix and suffixes prevents user from being able to save

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.1.1...v4.1.2)

  ### Bug Fixes
 * [MODORDSTOR-197](https://issues.folio.org/browse/MODORDSTOR-197) - Adding certain prefix and suffixes prevents user from being able to save

## 4.1.1 - Released

The focus of this release was to fix issue with logging and return an error for expense class

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.1.0...v4.1.1)

### Bug Fixes
* [MODINVOICE-207](https://issues.folio.org/browse/MODINVOICE-207) - No logging in honeysuckle version
* [MODINVOICE-205](https://issues.folio.org/browse/MODINVOICE-205) - Error must be returned in case of budget expense class mismatch

## 4.1.0 - Released

The focus of this release was to improve invoice Approval and Payment, prorated adjustments special cases processing, bug fixing

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.0.3...v4.1.0)

### Stories
* [MODINVOICE-203](https://issues.folio.org/browse/MODINVOICE-203)	Update transition to Approve considering the expense classes "Code"
* [MODINVOICE-202](https://issues.folio.org/browse/MODINVOICE-202)	Change direct usage of Monetary API to Finance module API	
* [MODINVOICE-199](https://issues.folio.org/browse/MODINVOICE-199)	Invoice line status is not updated	
* [MODINVOICE-190](https://issues.folio.org/browse/MODINVOICE-190)	mod-invoice: Update RMB		
* [MODINVOICE-186](https://issues.folio.org/browse/MODINVOICE-186)	Migrate mod-invoice to JDK 11		
* [MODINVOICE-181](https://issues.folio.org/browse/MODINVOICE-181)	Update Pending payments when exchange rate is edited	
* [MODINVOICE-176](https://issues.folio.org/browse/MODINVOICE-176)	Prevent the creation of a transaction for an inactive expense class	
* [MODINVOICE-174](https://issues.folio.org/browse/MODINVOICE-174)	Update transition to Approve considering the expense classes	
* [MODINVOICE-169](https://issues.folio.org/browse/MODINVOICE-169)	Block deletion of invoices and invoices lines for paid or approved invoice	
* [MODINVOICE-148](https://issues.folio.org/browse/MODINVOICE-148)	Related invoices not displaying on PO	
* [MODINVOICE-123](https://issues.folio.org/browse/MODINVOICE-123)	Update Voucher and voucherLInes when exchange rate is edited	
* [MODINVOICE-114](https://issues.folio.org/browse/MODINVOICE-114)	Check remaining amount expendable upon invoice approval	
* [MODINVOICE-105](https://issues.folio.org/browse/MODINVOICE-105)	Prorated Adjustments - prorating percentage adjustments	
* [MODINVOICE-59](https://issues.folio.org/browse/MODINVOICE-59)	Prorated adjustments part 2 - validation/special cases	

### Bug Fixes
* [MODINVOICE-204](https://issues.folio.org/browse/MODINVOICE-204)	Encumbrance not linked to payments and credits upon invoice payment	
* [MODINVOICE-198](https://issues.folio.org/browse/MODINVOICE-198)	Expense class not added to Pending payment	
* [MODINVOICE-193](https://issues.folio.org/browse/MODINVOICE-193)	Invoice loading stuck on Bugfest	
* [MODINVOICE-192](https://issues.folio.org/browse/MODINVOICE-192)	Cannot approve invoice when ledger's fiscalYearOne not equal to current
* [MODINVOICE-188](https://issues.folio.org/browse/MODINVOICE-188)	Adjustments fund distributions should counted during calculation of budget available amount for invoice approval
* [MODINVOICE-180](https://issues.folio.org/browse/MODINVOICE-180)	Check remaining amount expendable for active budgets only	


## 4.0.3 - Released

The focus of this release was to fix issue of stuck loading of invoices after an attempt to upload batch voucher on FTP

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.0.2...v4.0.3)

### Bug Fixes
* [MODINVOICE-193](https://issues.folio.org/browse/MODINVOICE-193) - Invoice loading stuck on Bugfest


## 4.0.2 - Released

The focus of this release was to fix pending payment creation and fix batch voucher export

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.0.1...v4.0.2)

### Bug Fixes
* [MODINVOICE-191](https://issues.folio.org/browse/MODINVOICE-191) - Batch voucher export returns an error with message "Batch voucher not found" for xml format
* [MODINVOICE-182](https://issues.folio.org/browse/MODINVOICE-182) - Link to encumbrance missed


## 4.0.1 - Released

The focus of this release was to honor accounting codes upon batch voucher generation, minor fixes of field mapping.

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.0.0...v4.0.1)

### Bug Fixes
* [MODINVOICE-177](https://issues.folio.org/browse/MODINVOICE-177) - Exclude vouchers with empty accountingCode from batchVoucher generation
* [MODINVOICE-172](https://issues.folio.org/browse/MODINVOICE-172) - Invoice requires an accounting code when "Export to accounting" is false
* [MODINVOICE-171](https://issues.folio.org/browse/MODINVOICE-171) - The BatchedVoucher.vendorInvoiceNo field populated with the wrong value
* [MODINVOICE-168](https://issues.folio.org/browse/MODINVOICE-168) - Populate field "voucherLine.fundDistribution.code" upon Invoice approval


## 4.0.0 - Released

The focus of this release was to create a batch and upload to FTP and set money aside as awaiting payment for Invoices that are 'Approved'

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v3.1.1...v4.0.0)

### Stories
* [MODINVOICE-167](https://issues.folio.org/browse/MODINVOICE-167) - mod-invoice: Update to RMB v30.0.1
* [MODINVOICE-163](https://issues.folio.org/browse/MODINVOICE-163) - Securing APIs by default
* [MODINVOICE-140](https://issues.folio.org/browse/MODINVOICE-140) - Create Pending payments upon invoice transition to approved
* [MODINVOICE-137](https://issues.folio.org/browse/MODINVOICE-137) - Implement "test" endpoint
* [MODINVOICE-136](https://issues.folio.org/browse/MODINVOICE-136) - Evaluate fund distributions when generating voucher
* [MODINVOICE-133](https://issues.folio.org/browse/MODINVOICE-133) - Implement the "upload" endpoint
* [MODINVOICE-132](https://issues.folio.org/browse/MODINVOICE-132) - Batch voucher generation and persistence

### Bug Fixes
* [MODINVOICE-170](https://issues.folio.org/browse/MODINVOICE-170) - Fix batch voucher file name generation
* [MODINVOICE-161](https://issues.folio.org/browse/MODINVOICE-161) - Fix batch voucher converting from JSON to XML
* [MODINVOICE-124](https://issues.folio.org/browse/MODINVOICE-124) - Limit document size

## 3.1.1 - Released

The main focus of this bugfix release was to fix issues

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v3.1.0...v3.1.1)

### Stories
* [MODINVOICE-109](https://issues.folio.org/browse/MODINVOICE-109) - Create payment or credit when Invoice marked as 'Paid

### Bug Fixes
* [MODINVOICE-100](https://issues.folio.org/browse/MODINVOICE-100) - Fix raml(contract) to return application/json responses wherever possible

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
