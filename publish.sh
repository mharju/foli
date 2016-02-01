#!/bin/bash

set -e
lein do clean, cljsbuild once min
pushd resources/public
    scp -r css js index.html $FOLI_USERNAME@$FOLI_SERVER:/usr/share/nginx/html/foli
popd
