# RDFSub

RDFSub is a brokered publish/subscribe protocol for triple patterns inspired by [W3C WebSub](https://www.w3.org/TR/websub/) and [LDScript and Linked Functions](http://ns.inria.fr/sparql-extension/).

## Protocol Overview

The protocol involves three roles:

- **Publisher**: An owner of RDF data on one or multiple topics.

- **Subscriber**: An entity that wants to be notified of new results to triple pattern queries on one or multiple topics. A Subscriber is identified by a callback URL and can register multiple queries.

- **Hub**: An intermediary that caches topic-based RDF data from Publishers and notifies Subscribers when data updates trigger new results to their queries.

Protocol flow:

1. Subscribers discover Hubs (e.g., via `Link` header fields advertised by Publishers or through hypermedia search) and register triple pattern queries on topics of interest.

2. Publishers notify their Hubs when their topic-based RDF data has changed.

3. Hubs dispatch notifications to Subscribers whose triggering functions match.


## Subscribing and Unsubscribing

To subscribe to a Hub, a Subscriber must provide:

- a tripple pattern query on a topic of interest

- a Linked Function used to trigger the execution of the query

- a callback URL that identifies the Subscriber and is used to distribute new results to queries

Other optional subscription properties:

- a lease used to determine the subscription's lifetime (e.g., in seconds)

- a secret used for authorized content distribution 

When data on the topic of interest is updated, the Linked Function provided by the Subscriber is invoked with two arguments: the list of triples deleted in the update and the list of triples inserted in the update. The triggering function should return either true or false.

A sample Linked Function written in `LDScript` that always returns true (will always match):

    @public
    function us:trigger(del, ins) {
      true
    }

A Subscriber creates a subscription to a Hub via an `HTTP POST` request to the Hub's subscriber interface. The representation sent in the body of the request must identify the subscription to be created with a null relative IRI.

The Hub replies with a `202 Accepted` status code to indicate that the request was received and will be verified and validated. The Hub must verify the intent of the Subscriber using the provided callback URL. THe Hub may also validate the syntax of the Linked Function provided by the Subscriber.

[TODO] Specify how the Hub verifies the subscriber's intent. This can be similar to how it is achieved in [W3C WebSub (see Section 5.3)](https://www.w3.org/TR/websub/#hub-verifies-intent).

Sample subscription request and response: 

    POST /subscription HTTP/1.1
    Host: localhost:8090
    Content-Type: text/turtle
    
    <> a us:Subscription ;
      us:callback <http://localhost:1080/callback> ;
      us:trigger <http://localhost:1080/trigger> ;
      us:query "construct from <http://hyperagents.org/> where { ?x ?y ?z }" .
    
    HTTP/1.1 202 Accepted
    content-length: 0

In this example, the Subscriber registers a SPARQL CONSTRUCT query for the topic `http://hyperagents.org`.

## Publishing

[TODO] to be written

## Query Processing and Content Distribution

When the data for a given topic is updated, Hubs process all registered queries for which the triggering function evaluates to `true`. THe results are then sent to Subscribers via the callback URLs.

Hubs may apply query limiting policies or their own policies for content distribution.
