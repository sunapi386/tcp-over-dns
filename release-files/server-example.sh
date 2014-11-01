#!/bin/bash
DOMAIN=dnstunnel.example.com
FWDPORT=22
FWDADDR=127.0.0.1
MTU=1500
LOGLEVEL=3
java -jar tcp-over-dns-server.jar --domain $DOMAIN --forward-port $FWDPORT --forward-address $FWDADDR --mtu $MTU --log-level $LOGLEVEL

