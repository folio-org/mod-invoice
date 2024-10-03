## 5.9.0 - Unreleased

## 5.8.3 - Released (Quesnelia R1 2024)

This release focused on fixing security vulnerabilities
[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.8.2...v5.8.3)

### Bug Fixes
* [MODINVOICE-558](https://folio-org.atlassian.net/browse/MODINVOICE-558) - Fix Vert.x, Netty, Apache SSHD/SFTP - CVE-2024-41909

## 5.8.2 - Released (Quesnelia R1 2024)
This release focused on adding ability to search by location and holding in POL  
[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.8.1...v5.8.2)

### Bug Fixes
* [MODORDERS-1085](https://folio-org.atlassian.net/browse/MODORDERS-1085) - Add ability to search by location and holding in POL


## 5.8.1 - Released (Quesnelia R1 2024)
The focus of this release was to fix acquisition check behaviour and improve consistency between invoice and encumbrance

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.8.0...v5.8.1)

### Bug Fixes
* [MODORDERS-1073](https://folio-org.atlassian.net/browse/MODORDERS-1073) - Invoice encumbrance link not removed because of acquisition unit
* [MODINVOICE-540](https://folio-org.atlassian.net/browse/MODINVOICE-540) - Mod-invoice tries to release encumbrances that are already released
* [MODINVOICE-516](https://folio-org.atlassian.net/browse/MODINVOICE-516) - Invoice transactions should not be changed when acquisition check was failed

## 5.8.0 - Released (Quesnelia R1 2024)
The focus of this release was to fix bugs and make improvement in transaction call and error codes

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.7.0...v5.8.0)

### Stories
* [MODSOURMAN-1022](https://issues.folio.org/browse/MODSOURMAN-1022) - Remove step of initial saving of incoming records to SRS
* [MODINVOICE-534](https://folio-org.atlassian.net/browse/MODINVOICE-534) - Rethrow user not linked order's acquisition unit error
* [MODINVOICE-531](https://folio-org.atlassian.net/browse/MODINVOICE-531) - Upgrade RAML Module Builder
* [MODINVOICE-502](https://folio-org.atlassian.net/browse/MODINVOICE-502) - Update RMB and vertx to the latest version
* [MODINVOICE-482](https://folio-org.atlassian.net/browse/MODINVOICE-482) - Accumulate all transactions in holder to make only single call to mod-finance
* [MODINVOICE-479](https://issues.folio.org/browse/MODINVOICE-479) - Add Kafka event deduplication mechanism for creating invoices
* [MODINVOICE-361](https://folio-org.atlassian.net/browse/MODINVOICE-361) - Logging improvement
* [MODINVOICE-302](https://folio-org.atlassian.net/browse/MODINVOICE-302) - Improve backend error reporting by including cause in error message systematically
* [MODINVOICE-93](https://folio-org.atlassian.net/browse/MODINVOICE-93) - Approve invoice permission and require approval to mark as paid

### Bug Fixes
* [MODINVOICE-532](https://folio-org.atlassian.net/browse/MODINVOICE-532) - Missing interface dependencies in module descriptor
* [MODINVOICE-523](https://folio-org.atlassian.net/browse/MODINVOICE-523) - After resaving the configurations with empty credentials, an NPE occurs during manual export
* [MODINVOICE-514](https://folio-org.atlassian.net/browse/MODINVOICE-514) - Uninformative error in invoice payment due to user not linked to the Purchase Order's acquisition unit
* [MODINVOICE-450](https://folio-org.atlassian.net/browse/MODINVOICE-450) - Invoice approval is successful but throws error message

### Tech Debt
* [MODINVOICE-522](https://folio-org.atlassian.net/browse/MODINVOICE-522) - Adding validation to exclude cases where the
  'directory' field in batch group configuration consists solely of spaces

### Breaking changes
* DB schema introduced for mod-invoice

### Dependencies
* Bump `raml` from `35.0.1` to `35.2.0`
* Bump `vertx` from `4.3.4` to `4.5.4`
* Bump `mod-di-converter-storage-client` from `2.1.0` to `2.1.8`
* Bump `folio-kafka-wrapper` from `3.0.0` to `3.1.0`

## 5.7.0 - Released Poppy R2 2023
The focus of this release was to implement Vertx SFTP client for Batch Voucher Export and implement pay against previous fiscal years

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.6.0...v5.7.0)

### Stories
* [MODINVOICE-501](https://issues.folio.org/browse/MODINVOICE-501) - Add missed permissions to module descriptor
* [MODINVOICE-494](https://issues.folio.org/browse/MODINVOICE-494) - Support batch voucher sftp connection test
* [MODINVOICE-493](https://issues.folio.org/browse/MODINVOICE-493) - Ignore nextPolNumber for pol numbers generation from request payload
* [MODINVOICE-489](https://issues.folio.org/browse/MODINVOICE-489) - Upgrade folio-kafka-wrapper to 3.0.0 version
* [MODINVOICE-484](https://issues.folio.org/browse/MODINVOICE-484) - Update to Java 17 mod-invoice
* [MODINVOICE-481](https://issues.folio.org/browse/MODINVOICE-481) - Save invoice fiscal year if it is undefined after any fund distribution change on INVOICE
* [MODINVOICE-474](https://issues.folio.org/browse/MODINVOICE-474) - Update the encumbrance when using a past fiscal year
* [MODINVOICE-473](https://issues.folio.org/browse/MODINVOICE-473) - Save invoice fiscal year if it is undefined after any fund distribution change on invoice line
* [MODINVOICE-471](https://issues.folio.org/browse/MODINVOICE-471) - Restrict approving/paying an invoice against past fiscal year with a new permission
* [MODINVOICE-466](https://issues.folio.org/browse/MODINVOICE-466) - Update DTO schema with adding fiscalYearId to PUT payload
* [MODINVOICE-465](https://issues.folio.org/browse/MODINVOICE-465) - Provide endpoint to retrieve fiscal year for invoice
* [MODINVOICE-463](https://issues.folio.org/browse/MODINVOICE-463) - Provide fiscalYear field for pending payments, payments and credits
* [MODINVOICE-455](https://issues.folio.org/browse/MODINVOICE-455) - Update models schema with new fields for ftp configuration
* [MODINVOICE-454](https://issues.folio.org/browse/MODINVOICE-454) - Implement Vertx SFTP client for Batch Voucher Export
* [MODINVOICE-443](https://issues.folio.org/browse/MODINVOICE-443) - Update dependent raml-util

### Bug Fixes
* [MODINVOICE-490](https://issues.folio.org/browse/MODINVOICE-490) - Reflection access to private fields fails in Java 17
* [MODINVOICE-487](https://issues.folio.org/browse/MODINVOICE-487) - Encumbrance amount updated incorrectly after cancelling related invoice
* [MODINVOICE-488](https://issues.folio.org/browse/MODINVOICE-488) - Invoice cannot be approved when an invoice-line is multi-fund distributed by amounts with an invoice-level adjustment
* [MODINVOICE-480](https://issues.folio.org/browse/MODINVOICE-480) - Batch voucher export hangs at 'Pending' indefinitely for batches with multiple vouchers, no export file produced
* [MODINVOICE-477](https://issues.folio.org/browse/MODINVOICE-477) - Invoice cannot be approved when balance is close to the encumbrance available balance
* [MODINVOICE-467](https://issues.folio.org/browse/MODINVOICE-467) - Break circular module invocations
* [MODINVOICE-464](https://issues.folio.org/browse/MODINVOICE-464) - Error when approving an invoice with a different fund than in po line
* [MODINVOICE-460](https://issues.folio.org/browse/MODINVOICE-460) - Invalid usage of semaphores ignores limit of active threads
* [MODINVOICE-453](https://issues.folio.org/browse/MODINVOICE-453) - Exchange rates cannot be updated on invoice lines with multiple funds
* [MODDICORE-306](https://issues.folio.org/browse/MODDICORE-306) - Update mod-di-converter-storage-client and data-import-processing-core dependencies

### Dependencies
* Bump `java version` from `11` to `17`

## 5.6.0 - Orchid R1 2023
The focus of this release was to migrate to Vertx future and improve logging 

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.5.0...v5.6.0)

### Stories
* [MODINVOICE-428](https://issues.folio.org/browse/MODINVOICE-428) - Correct http response codes for invoice validation
* [MODINVOICE-431](https://issues.folio.org/browse/MODINVOICE-431) - Replace FolioVertxCompletableFuture usage
* [MODINVOICE-434](https://issues.folio.org/browse/MODINVOICE-434) - Logging improvement - Configuration

### Bug Fixes
* [MODINVOICE-440](https://issues.folio.org/browse/MODINVOICE-440) - Invoice in Reviewed status but payments already created
* [MODINVOICE-442](https://issues.folio.org/browse/MODINVOICE-442) - Request-URI Too Long when trying to cancel invoice with more than 40 invoice lines
* [MODINVOICE-446](https://issues.folio.org/browse/MODINVOICE-446) - Encumbrance for Ongoing order remains "Released" after cancelling related invoice
* [MODINVOICE-449](https://issues.folio.org/browse/MODINVOICE-449) - Adjustments cause blocker on invoices

## 5.5.0 - Nolana R3 2022
The focus of this release was to Upgrade RMB up to 35.0.1

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.4.0...v5.5.0)

### Stories
* [FOLIO-3604](https://issues.folio.org/browse/FOLIO-3604) FolioVertxCompletableFuture copyright violation
* [MODINVOICE-430](https://issues.folio.org/browse/MODINVOICE-430) Upgrade RAML Module Builder

## 5.4.0 - Released Morning Glory 2022 R2
The focus of this release was to update core dependencies and fix invoice payments

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.3.0...v5.4.0)

### Stories
* [MODINVOICE-410](https://issues.folio.org/browse/MODINVOICE-410) - mod-invoice: Upgrade RAML Module Builder
* [MODINVOICE-373](https://issues.folio.org/browse/MODINVOICE-373) - Include "Invoice date" and "Terms" field in voucher export for each batched voucher
* [MODINVOICE-372](https://issues.folio.org/browse/MODINVOICE-372) - Update version of data-import-processing-core
* [MODINVOICE-328](https://issues.folio.org/browse/MODINVOICE-328) - Allow editing of subscription dates after an invoice is paid

### Bug Fixes
* [MODINVOICE-387](https://issues.folio.org/browse/MODINVOICE-387) - Insufficient fund validation to approve invoice
* [MODINVOICE-401](https://issues.folio.org/browse/MODINVOICE-401) - Allow user to attach or remove files from approved/paid invoice
* [MODINVOICE-402](https://issues.folio.org/browse/MODINVOICE-402) - User with certain permissions can not add invoice line
* [MODINVOICE-404](https://issues.folio.org/browse/MODINVOICE-404) - User with certain permissions can not save changed fund name in invoice line
* [MODINVOICE-377](https://issues.folio.org/browse/MODINVOICE-377) - Cannot add or remove tags from paid invoice


## 5.3.0 - Released
The focus of this release was to fix transaction and vouchers processing

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.2.0...v5.3.0)

### Stories
* [MODINVOICE-262](https://issues.folio.org/browse/MODINVOICE-262) - Include "Invoice date" and "Terms" field in voucher export for each batched voucher

### Bug Fixes
* [MODINVOICE-360](https://issues.folio.org/browse/MODINVOICE-360) - Unrelease auto-released encumbrances when cancelling an invoice
* [MODINVOICE-357](https://issues.folio.org/browse/MODINVOICE-357) - Error message does not indicate what Fund does not have money
* [MODINVOICE-356](https://issues.folio.org/browse/MODINVOICE-356) - Fix progress bar stuck behaviour after the RecordTooLargeException
* [MODINVOICE-347](https://issues.folio.org/browse/MODINVOICE-347) - Unable to download batch vouchers
* [MODINVOICE-335](https://issues.folio.org/browse/MODINVOICE-335) - Creating multiple invoice lines per invoice fails
* [MODINVOICE-313](https://issues.folio.org/browse/MODINVOICE-313) - Display Batch export status as "Generated" when export created
* [MODINVOICE-312](https://issues.folio.org/browse/MODINVOICE-312) - Voucher line missing from batch voucher export


## 5.2.1 - Unreleased
* [MODINVOICE-314](https://issues.folio.org/browse/MODINVOICE-314) - Provide recordId header for data-import events correlation

## 5.2.0 - Released

The focus of this release was to adjust and fix invoice approval handling

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.0.3...v5.1.0)

### Stories
* [MODINVOICE-294](https://issues.folio.org/browse/MODINVOICE-294) - Don't allow to pay for the invoice which was not approved before
* [MODINVOICE-275](https://issues.folio.org/browse/MODINVOICE-275) - Invoice poNumbers needs to be updated when an invoice line is deleted
* [MODINVOICE-251](https://issues.folio.org/browse/MODINVOICE-251) - Remove zipping mechanism for data import event payloads and use cache for params
* [MODINVOICE-178](https://issues.folio.org/browse/MODINVOICE-178) - Update invoice.poNumbers field when invoiceLine is being linked to poLine
* [MODINVOICE-56](https://issues.folio.org/browse/MODINVOICE-56) - API tests for voucher/voucherLine creation

### Bug Fixes
* [MODINVOICE-306](https://issues.folio.org/browse/MODINVOICE-306) - Acq unit in invoice field mapping profile causes import to complete with errors
* [MODINVOICE-290](https://issues.folio.org/browse/MODINVOICE-290) - Encumbrance not released when Invoice approved if invoice has diff Fund from POL
* [MODINVOICE-279](https://issues.folio.org/browse/MODINVOICE-279) - Approval allowed for 0 amount invoice and invoices with 0 amount invoice lines
* [MODINVOICE-233](https://issues.folio.org/browse/MODINVOICE-233) - Integration test check-invoice-and-invoice-lines-deletion-restrictions fails randomly


## 5.1.0 - Released

The focus of this release was to fix populating fields of the voucher lines

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.0.3...v5.1.0)

### Stories
* [MODINVOICE-241](https://issues.folio.org/browse/MODINVOICE-241) - Populate Vendor primary address on GET /voucher/vouchers/id
* [MODINVOICE-240](https://issues.folio.org/browse/MODINVOICE-240) - Include vendor organization address in voucher export for each batched voucher
* [MODINVOICE-238](https://issues.folio.org/browse/MODINVOICE-238) - mod-invoice: Update RMB
* [MODINVOICE-234](https://issues.folio.org/browse/MODINVOICE-234) - Update export batch voucher logic for supporting "enclosureNeeded" and "accountNo"
* [MODINVOICE-225](https://issues.folio.org/browse/MODINVOICE-225) - Prevent approval of invoice when organization IS NOT a vendor
* [MODINVOICE-221](https://issues.folio.org/browse/MODINVOICE-221) - Relink invoice with new order if POL link changed in invoice line

### Bug Fixes
* [MODINVOICE-255](https://issues.folio.org/browse/MODINVOICE-255) - Credit transaction given value of $0
* [MODINVOICE-249](https://issues.folio.org/browse/MODINVOICE-249) - The relation between the order and the invoice is not deleted if the invoice is last online
* [MODINVOICE-246](https://issues.folio.org/browse/MODINVOICE-246) - Voucher line reference to the invoice line is empty
* [MODINVOICE-245](https://issues.folio.org/browse/MODINVOICE-245) - Incorrect split funds with an odd number of pennies for vouchers
* [MODINVOICE-232](https://issues.folio.org/browse/MODINVOICE-232) - Cannot split funds on an Invoice with an odd number of pennies as the cost


## 5.0.3 - Released

The focus of this release was to fix populating fields of the voucher lines

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.0.3...v5.0.2)

### Stories
* [MODINVOICE-241](https://issues.folio.org/browse/MODINVOICE-241) - Populate Vendor primary address on GET /voucher/vouchers/id
* [MODINVOICE-240](https://issues.folio.org/browse/MODINVOICE-240) - Include vendor organization address in voucher export for each batched voucher

### Bug Fixes
* [MODINVOICE-249](https://issues.folio.org/browse/MODINVOICE-249) - The relation between the order and the invoice is not deleted if the invoice is last online


## 5.0.2 - Released

The focus of this release was to fix populating fields of the voucher lines

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.0.1...v5.0.2)

### Bug Fixes
* [MODINVOICE-246](https://issues.folio.org/browse/MODINVOICE-246) - Voucher line reference to the invoice line is empty


## 5.0.1 - Released

The focus of this release was to fix invoice calculation issues, extend voucher and batch voucher schemas with the new fields

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v5.0.0...v5.0.1)

### Stories
* [MODINVOICE-234](https://issues.folio.org/browse/MODINVOICE-234) - Update export batch voucher logic for supporting "enclosureNeeded" and "accountNo"

### Bug Fixes
* [MODINVOICE-245](https://issues.folio.org/browse/MODINVOICE-245) - Incorrect split funds with an odd number of pennies for vouchers
* [MODINVOICE-232](https://issues.folio.org/browse/MODINVOICE-232) - Cannot split funds on an Invoice with an odd number of pennies as the cost


## 5.0.0 - Released

The focus of this release was to update RMB, major changes in the display of lock total now this is a number. 
Also integration with EDIFACT format was done. 

[Full Changelog](https://github.com/folio-org/mod-invoice/compare/v4.1.3...v5.0.0)

### Technical tasks
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
