@SET DOMAIN=tunnel.example.com
@SET LISTPORT=8080
@SET LISTADDR=127.0.0.1
@SET INTERVAL=20
@SET MTU=1500
@SET TRUNCBYTES=0
@SET LOGLEVEL=3
@SET QUERYTYPE=TXT
@SET CLIENTENC=base63
java -jar tcp-over-dns-client.jar --domain %DOMAIN% --listen-port %LISTPORT% --listen-address %LISTADDR% --interval %INTERVAL% --mtu %MTU% --trunc-bytes %TRUNCBYTES% --query-type %QUERYTYPE% --client-enc %CLIENTENC% --log-level %LOGLEVEL%
