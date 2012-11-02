#!/bin/bash
#http://dist.neo4j.org/neo4j-community-1.9.M01-unix.tar.gz
VERSION="1.9.M01"
DIR="neo4j-community-$VERSION"
FILE="$DIR-unix.tar.gz"
SERVER_PROPERTIES_FILE="lib/neo4j/conf/neo4j-server.properties"
#set a default neo4j port if none has been set
NEO4J_PORT=${NEO4J_PORT:="7474"}

if [[ ! -d lib/$DIR ]]; then
    wget http://dist.neo4j.org/$FILE
    tar xvfz $FILE &> /dev/null
    rm $FILE
    [[ ! -d lib ]] && mkdir lib
    mv $DIR lib/
    [[ -h lib/neo4j ]] && unlink lib/neo4j
    ln -fs $DIR lib/neo4j
fi

if grep 7474 $SERVER_PROPERTIES_FILE > /dev/null; then
    sed -i s/7474/$NEO4J_PORT/g $SERVER_PROPERTIES_FILE #change port
    echo "cache_type=none" >> $SERVER_PROPERTIES_FILE # disable cache
fi
