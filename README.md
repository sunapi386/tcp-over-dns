tcp-over-dns
============

tcp-over-dns contains a special dns server and a special dns client. The client and server work in tandem to provide a TCP (and UDP!) tunnel through the standard DNS protocol.

This is similiar to the defunct NSTX dns tunelling software. The purpose of this software to is succeed where NSTX failed. For me at least, all NSTX tunnels disconnect within tens of seconds in real world situations. tcp-over-dns was written to be quite robust while at the same time providing acceptable bandwidth speeds.

See this write-up about using the software for more information.

Internally, this program uses the very excellent dnsjava library. As well as the jargs library and 7-zip LZMA library.

Features:
Windows, Linux, Solaris compatibility (BSD/OSX reports are welcome) (Anything with a Java 6 VM)
Sliding window packet transfers for increased speed and reliability.
Runtime selective LZMA compression.
TCP and UDP traffic tunneling.
Software Requirements:
Java runtime environment 6.0+
Server requires root/admin port 53 access.
Execute java -jar tcp-over-dns-client.jar for a list of client options, likewise for the server.

Licensed under a BSD license. See LICENSE.txt in the included files.
http://analogbit.com/software/tcp-over-dns
