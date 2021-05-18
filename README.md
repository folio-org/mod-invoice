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


## Interaction with Kafka


There are several properties that should be set for modules that interact with Kafka: **KAFKA_HOST, KAFKA_PORT, OKAPI_URL, ENV**(unique env ID).
After setup, it is good to check logs in all related modules for errors. Data import consumers and producers work in separate verticles that are set up in RMB's InitAPI for each module. That would be the first place to check deploy/install logs.

**Environment variables** that can be adjusted for this module and default values:
* "_mod.invoice.kafka.DataImportConsumerVerticle.instancesNumber_": 1
* "_mod.invoice.kafka.DataImportConsumer.loadLimit_": 5
* "*mod.invoice.kafka.DataImportConsumerVerticle.maxDistributionNumbe*r": 100
* "_dataimport.consumer.verticle.mandatory_": false       (should be set to true in order to fail the module at start-up if data import Kafka consumer creation failed)

#### Note
**These variables are relevant for the **Iris** release. Module version: 5.0.0 (5.0.1, 5.0.2, 5.0.3).**

### Issue tracker

See project [MODINVOICE](https://issues.folio.org/browse/MODINVOICE)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
