@SET DOMAIN=dnstunnel.example.com
@SET FWDPORT=22
@SET FWDADDR=127.0.0.1
@SET MTU=1500
@SET LOGLEVEL=3
java -jar tcp-over-dns-server.jar --domain %DOMAIN% --forward-port %FWDPORT% --forward-address %FWDADDR% --mtu %MTU% --log-level %LOGLEVEL%
