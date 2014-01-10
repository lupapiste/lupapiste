# lupapalvelu

A website written in noir.

## Requirements

mongodb running on localhost, on default port

## Usage

lein deps
lein run

# testing
lein midje

# development

lein eclipse

# packaging

lein with-profile uberjar uberjar

# moving it to da Pilvi

scp target/lupapalvelu-0.1.0-SNAPSHOT-standalone.jar sade@129.35.250.30:~/lupapalvelu/develop.jar

# running commands

[~]$ http post localhost:8000/rest/command Authorization:"apikey 502cb9e58426c613c8b85abc" command=ping

HTTP/1.1 200 OK
Content-Length: 25
Content-Type: application/json;charset=utf-8
Date: Mon, 20 Aug 2012 10:38:15 GMT
Server: Jetty(7.6.1.v20120215)

{
    "ok": true, 
    "text": "pong"
}