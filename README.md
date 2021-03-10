# mod-invoice

Copyright (C) 2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This is the Invoice business logic module.

## Additional information

NOTE: Only in case an acquisition unit has to be assigned to the Invoice, it is required that user should have an
extra permission `invoices.acquisitions-units-assignments.assign` to create an invoice.

NOTE: Only in case an acquisition units list has to be changed for the Invoice, it is required that user should have an
extra permission `invoices.acquisitions-units-assignments.manage` to update an invoice.

### Integration

#### Data import Kafka consumer

In order to fail the module start-up when data import Kafka consumer creation is failed the one should set 
`dataimport.consumer.verticle.mandatory` variable to `true`.

### Issue tracker

See project [MODINVOICE](https://issues.folio.org/browse/MODINVOICE)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
