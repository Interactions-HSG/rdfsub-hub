#!/bin/bash

echo "Creating 1st subscription:"

curl -i -X POST 'http://localhost:8090/subscription' \
-H 'Content-Type: text/turtle' \
--data-raw '<> a us:Subscriber ;
us:callback <http://localhost:1080/callback> ;
us:trigger <http://localhost:1080/trigger> ;
us:query "select * from <http://hyperagents.org/> where { ?x ?y ?z }" .'

sleep 1

echo "Creating 2nd subscription:"

curl -i -X POST 'http://localhost:8090/subscription' \
-H 'Content-Type: text/turtle' \
--data-raw '<> a us:Subscriber ;
us:callback <http://localhost:1080/callback> ;
us:trigger <http://localhost:1080/trigger> ;
us:query "construct from <http://hyperagents.org/> where { ?x ?y ?z }" .'

