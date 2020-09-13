#!/bin/bash

echo "Publishing John's social graph. He knows: Bob, Jane, Mark, Kate."

curl -i -X POST 'http://localhost:8080/sparql' \
-H 'Content-Type: application/sparql-update' \
--data-raw '@event
insert data {
  us:John foaf:knows us:Bob; foaf:knows us:Jane; foaf:knows us:Mark; foaf:knows us:Kate .
}

@update
function us:update(q, del, ins) {
    for ((s p o) in del) {
        us:notify("http://localhost:8090/publish", "delete", "http://hyperagents.org/", s, p, o)
    } ;
    for ((s p o) in ins) {
        us:notify("http://localhost:8090/publish", "insert", "http://hyperagents.org/", s, p, o)
    } 
}

function us:notify(hub, action, topic, s, p, o) {
    xt:read(st:format("%s?action=%s&topic=%s&subject=%s&predicate=%s&object=%s", 
        hub, action, encode_for_uri(topic), encode_for_uri(s), encode_for_uri(p), encode_for_uri(o)))
}'

sleep 1

while :
do

sleep 5

echo "Updating John's social graph: removing Bob and adding Jane."

curl -i -X POST 'http://localhost:8080/sparql' \
-H 'Content-Type: application/sparql-update' \
--data-raw '@event
delete {
  us:John foaf:knows us:Bob
}
insert {
  us:John foaf:knows us:Jane
}
where {  }

@update
function us:update(q, del, ins) {
    for ((s p o) in del) {
        us:notify("http://localhost:8090/publish", "delete", "http://hyperagents.org/", s, p, o)
    } ;
    for ((s p o) in ins) {
        us:notify("http://localhost:8090/publish", "insert", "http://hyperagents.org/", s, p, o)
    } 
}

function us:notify(hub, action, topic, s, p, o) {
    xt:read(st:format("%s?action=%s&topic=%s&subject=%s&predicate=%s&object=%s", 
        hub, action, encode_for_uri(topic), encode_for_uri(s), encode_for_uri(p), encode_for_uri(o)))
}'

sleep 5

echo "Updating John's social graph: removing Jane and adding Bob."

curl -i -X POST 'http://localhost:8080/sparql' \
-H 'Content-Type: application/sparql-update' \
--data-raw '@event
delete {
  us:John foaf:knows us:Jane
}
insert {
  us:John foaf:knows us:Bob
}
where {  }

@update
function us:update(q, del, ins) {
    for ((s p o) in del) {
        us:notify("http://localhost:8090/publish", "delete", "http://hyperagents.org/", s, p, o)
    } ;
    for ((s p o) in ins) {
        us:notify("http://localhost:8090/publish", "insert", "http://hyperagents.org/", s, p, o)
    } 
}

function us:notify(hub, action, topic, s, p, o) {
    xt:read(st:format("%s?action=%s&topic=%s&subject=%s&predicate=%s&object=%s", 
        hub, action, encode_for_uri(topic), encode_for_uri(s), encode_for_uri(p), encode_for_uri(o)))
}'

done