# imesc

Escalation System.  See section "Specification" below.

This project is currently a research playground.

## Installation

TODO

## Usage

    $ java -jar imesc-0.1.0-standalone.jar [args]

## Options

None.

## Examples

...

### Bugs

...

## Development

Run tests with one of the following:

```
lein test
lein test :integration
lein test :all
```

The first form should run only unit tests.

## Design Decisions

We are not using multiple instances of the service because we would have to
coordinate multiple schedulers.

If we use only one instance, we can monitor it and restart it if it hangs. E.g.
if the period of checking for alarms is 30 seconds, and if we are able to detect
that the service is down within 20 seconds by periodically checking it, then the
downtime could be reduced to an acceptable level.

We are using Kafka instead of HTTP web service for input requests because
otherwise we would need a load balancer and multiple instances of the service to
ensure high availability, and we trying to avoid multiple instances for the
reasons stated above.

## Specification

This section contains specification of various traits of this software. Based on
these, we plan to measure how well the code structure maps to these traits.

* The system operates in three phases: receiving requests to initiate escalation
  processes, activating scheduled notifications when their time comes, and
  delivering notifications through different notification channels (such as
  email, phone calls etc). The phases are handled by the initiator, activator
  and notifiers, respectively.
* The initiator reads requests from a Kafka topic and stores notification data
  into the database.
* Requests can initiate an escalation process or cancel it.
* There can only be one active escalation process for a restaurant. Requests to
  initiate a process which already exists should be ignored.
* The only way to stop an escalation process is by using a cancel request. (That
  is, an escalation process cannot be canceled by, for example, answering a
  phone call.)
* Unrecognized/invalid requests should be logged and ignored.
* Requests contain a list of notifications which need to be delivered when their
  time comes.
* Data on currently active escalation processes are kept in the database.
* Process data contain timestamped notifications and a timestamp indicates when
  a notification should be delivered.
* The activator periodically polls the database to find notifications which
  should be delivered based on their timestamp and the current time.
* A notification is delivered by sending a request to the appropriate notifier.
* There are three notifiers: email, phone call and console printer.
* Each notifier receives requests through a notifier-specific Kafka requests
  topic (e.g. topic.email.requests).

## Research questions

- Code optimized for human reading: what does it look like and how do we write
  it? The code should primarily "talk" to people and not machines.

Measure the following in the code:

1. How easy is it to determine where to start reading the code when trying to
   find some property of the software?
2. How many different functions does one have to open (and start to read) to
   become reasonably confident that a property holds?
3. How many lines of code does one have to read to become reasonably confident
   that a property holds?

## License

Copyright © 2018 Ingemark d.o.o.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
