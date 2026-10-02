#!/usr/bin/env python3
"""
Builds app/src/main/assets/country.mmdb, the offline IP-to-country table behind the Map tab.

Data: "server-country" from https://github.com/sapics/ip-location-db, published under the
Open Data Commons Public Domain Dedication and License (PDDL 1.0) - free use, including
commercial use and redistribution inside the app, with no attribution requirement and no
rule to delete older copies. It is compiled from Regional Internet Registry delegation
statistics, public BGP routing archives (Route Views, RIPE RIS) and operator geofeeds, and
it places an address where the server sits rather than where its users are, which is what
a map of the servers apps talk to should show.

The output uses the same MMDB format and the same record shape ({"country": {"iso_code"}})
the app has always read, so the reader code does not care which dataset produced it.

Refreshing the data is optional - country allocations change slowly - but when you want to:

    pip install mmdb-writer netaddr
    python tools/build_geoip.py

then rebuild the app. Nothing here runs at build time or on the phone.

GeoIP in this app is for the map only. It never takes part in a blocking decision.
"""
import csv
import io
import os
import sys
import urllib.request

from mmdb_writer import MMDBWriter
from netaddr import IPSet, IPRange

BASE = "https://raw.githubusercontent.com/sapics/ip-location-db/main/server-country/"
SOURCES = ("server-country-ipv4.csv", "server-country-ipv6.csv")
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "country.mmdb")


def fetch(name):
    with urllib.request.urlopen(BASE + name, timeout=300) as r:
        return r.read().decode("utf-8")


def main():
    by_country = {}
    rows = 0
    for name in SOURCES:
        for row in csv.reader(io.StringIO(fetch(name))):
            if len(row) < 3 or len(row[2]) != 2:
                continue
            by_country.setdefault(row[2].upper(), []).append(IPRange(row[0], row[1]))
            rows += 1
    if rows < 100_000:
        sys.exit("Only %d ranges downloaded - refusing to write a truncated table." % rows)

    writer = MMDBWriter(
        ip_version=6,
        ipv4_compatible=True,
        database_type="TacU-NS-Country",
        languages=["en"],
        description={"en": "server-country (sapics/ip-location-db), PDDL 1.0"},
    )
    for cc, ranges in sorted(by_country.items()):
        writer.insert_network(IPSet(ranges), {"country": {"iso_code": cc}})
    writer.to_db_file(OUT)
    print("wrote %s from %d ranges in %d countries (%d bytes)"
          % (os.path.normpath(OUT), rows, len(by_country), os.path.getsize(OUT)))


if __name__ == "__main__":
    main()
