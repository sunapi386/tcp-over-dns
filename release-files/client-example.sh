#!/bin/bash
DOMAIN=tunnel.example.com
LISTPORT=8080
LISTADDR=127.0.0.1
INTERVAL=20
MTU=1500
TRUNCBYTES=0
LOGLEVEL=3
QUERYTYPE=TXT
CLIENTENC=base63
java -jar tcp-over-dns-client.jar --domain $DOMAIN --listen-port $LISTPORT --listen-address $LISTADDR --interval $INTERVAL --mtu $MTU --trunc-bytes $TRUNCBYTES --query-type $QUERYTYPE --client-enc $CLIENTENC --log-level $LOGLEVEL
