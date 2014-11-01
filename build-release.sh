#!/bin/bash
VER=$1
rm -rf ./bin
cd ./release-files
/usr/bin/zip -9 -r ../../tcp-over-dns-$VER.zip .
cd ..
/usr/bin/zip -9 -r ../tcp-over-dns-source-$VER.zip .


