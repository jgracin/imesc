# imesc

Escalation System.

## Installation

TODOs

## Usage

FIXME: explanation

    $ java -jar imesc-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

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

## License

Copyright © 2018 Ingemark d.o.o.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
