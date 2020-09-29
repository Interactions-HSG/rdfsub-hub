# RDFSub

RDFSub is an HTTP-based brokered publish/subscribe protocol that allows software clients to observe triple patterns. RDFSub was inspired by [W3C WebSub](https://www.w3.org/TR/websub/) and [LDScript and Linked Functions](http://ns.inria.fr/sparql-extension/).

[Andrei] General TODOs:
- align / integrate with the [W3C Linked Data Platform 1.0](http://www.w3.org/TR/ldp/)
- extend the specs to work with W3C WebSub Hubs when query processing is done by Publishers
- extend RDFSub to [CoAP](https://tools.ietf.org/html/rfc7252)

## Introduction

There are many solutions available to manage and query RDF datasets, but currently there is no solution designed specifically for observing RDF data. General-purpose solutions like W3C WebSub can be used instead, but such solutions provide coarse-grain access to RDF data: it is left to publishers of RDF data to decide what is the right granularity level for pushing data fragments to clients. This circumvents one of the main benefits of representing and exposing data in RDF: providing clients with fine-grained access to the data through triple pattern queries.

RDFSub is an attempt to fill this void: it gives clients fine-grained control over the RDF data they observe.

## Protocol Overview and Design Objectives

The protocol involves three roles:

- **Publisher**: An owner of RDF data on one or multiple topics.
- **Subscriber**: An entity that wants to be notified of new results to triple pattern queries on one or multiple topics. A Subscriber is identified by a callback URL.
- **Hub**: An intermediary that caches topic-based RDF data from Publishers and notifies Subscribers when data updates trigger new results to their queries.

Publishers can use multiple Hubs to distribute content to Subscribers, and Subscribers can choose Hubs that allow them to run queries across feeds from multiple Publishers. The latter is a notable difference from W3C WebSub that is enabled by the uniform representation of data in RDF.

RDFSub follows a number of design objectives:
- to provide Publishers with a flexible trade-off between offloading data to Hubs vs. maximizing the scalability of subscriptions
- to minimize the strain on Hubs in order to maximize their scalability
- to minimize the entry-barrier for adopting the protocol for both Publishers and Subscribers
- to maximize openness and discoverability of data by allowing both Subscribers and Publishers to discover Hubs dynamically at run time -- nevertheless, RDFSub can also be used in closed systems
- to maximize availability: RDFSub chooses availability over consistency of data across Hubs (cf. [CAP theorem](https://en.wikipedia.org/wiki/CAP_theorem))

Simplified protocol flow from a Subscriber's viewpoint:

1. A Subscriber discovers a Hub and registers a triple pattern query together with a triggering function for that query.

2. The Hub validates both the query and the triggering function, and may optionally verify the subscription with Publishers whose feeds are used in the query.

3. Publishers notify the Hub of changes to their topic-based RDF data.

4. On a data update, the Hub processes those queries that are impacted by the update and whose triggering functions fire.

## Subscribing and Unsubscribing

Subscription flow:
- Subscriber requests a subscription with the Hub, which includes the triple pattern query to be registered with an associated triggering function.
- The Hub dereferences the triggering function and validates the syntax (optional).
- The Hub validates the subscription with the Publishers whose feeds impact the query (optional).
- The Hub periodically reconfirms the subscription is still active (optional).

### Creating a Subscription

To create a subscription, a Subscriber must provide:
- a tripple pattern query on one or multiple topics of interest
- a Linked Function used to trigger the execution of the query
- a callback URL that identifies the Subscriber and is used to distribute new results for the registered query
- a lease used to determine the subscription's lifetime, e.g. in seconds (optional)
- a secret used for authorized content distribution (optional)

When data on one of the topics of interest is updated, the Linked Function provided by the Subscriber is invoked with two arguments: the list of triples deleted in the update and the list of triples inserted in the update. The triggering function must return either true or false.

A sample Linked Function written in [LDScript](http://ns.inria.fr/sparql-extension/) that always returns true (will always match):

    @prefix local: <http://localhost:1080/>
    
    @public
    function local:trigger(del, ins) {
      true
    }

A Subscriber creates a subscription to a Hub via an `HTTP POST` request to the Hub's subscriber interface. The representation sent in the body of the request must identify the subscription to be created with a null relative IRI.

The Hub replies with a `202 Accepted` status code to indicate that the request was received and will be verified and validated. The Hub must verify the intent of the Subscriber using the provided callback URL. The Hub should also validate the syntax of the Linked Function provided by the Subscriber and notify the Subscriber if something went wrong.

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

### Hub Verifies the Subscriber's Intent 

After validating the triggering function (and any other validations required by Publishers), the Hub must send the created subscription to the Subscriber via an `HTTP POST` request to the registered callback.

The Subscriber must check the subscription and must either echo the URI of the subscription with a `200 OK` to confirm the subscription, or reply with a `404 Not Found` if the subscription is not recognized    

### Hub Notifies Subscriber of Errors

If the syntax of a triggering function is invalid or if the triggering function raises a security exception (see Security Considerations), the Hub should notify the Subscriber by sending an `HTTP POST` request to the registered callback URL.

TODO: define the payload of the request; do we need another callback?

### Deleting a Subscription

To unsubscribe a registered query, the Subscriber sends an `HTTP DELETE` request to the URI of the subscription. The Hub should verify the intent of the Subscriber (TODO: write down the details).


## Publishing and Content Distribution

A Publisher can notify a Hub of data updates in one of two ways:
- The Publisher sends SPARQL updates to a Hub's SPARQL endpoint.
- [not viable] The Publisher notifies the Hub that data for a given topic has changed, the Hub then pulls the changes from the Publisher.

[Andrei] The latter "don't call us, we'll call you" approach would be nice because it gives Hubs more freedom in managing their data, which may help with scalability, but it's not viable: Publishers would have to keep track of changes and compile a list of changes until a Hub retrieves the changes -- and Publishers would have to do so for each Hub they use.

[Andrei] With the former approach, a Hub should maintain consistency of changes if updates on a given topic are performed very fast by a Publisher. This is just an implementation note for Hubs supporting high concurrency, not a problem in itself.

### Publisher Registration

Publisher registration flow:
- A Publisher sends a request to register with a Hub. The request must include a callback URL and may include one or multiple topic URIs.
- The Hub checks the intent of the Publisher by sending a [capability URL](https://www.w3.org/TR/capability-urls/) to the Publisher's callback URL.
- The Publisher pushes content to the capability URL received from the Hub using the [SPARQL 1.1 Protocol](https://www.w3.org/TR/sparql11-protocol/) and [SPARQL 1.1 Update](https://www.w3.org/TR/sparql11-update/) operations.

Publishers can update their list of topic URIs at any time. When a Publisher registers a topic, the topic URI must be in the same namespace with the Publisher's callback URL. This ensures that the Publisher is the authority for those topic URIs.

TODO: add sample request / response for Publisher registration

### The Cold Start Problem

[Andrei] To be considered: when a Publisher starts pushing data to a new Hub, there is a cold start problem -- the Publisher must push all the data required to perform certain queries. It could be the case that some Hubs hold more data than others and can provide better responses. This goes back to availability over consistency (see design objectives).

### Hub Verifies the Publisher's Intent

TODO: write down the details

### Query Processing and Content Distribution

When the data for a given topic is updated, the Hub processes all registered queries for which the triggering functions evaluate to `true`. The results are then sent to Subscribers via their callback URLs.

Hubs may apply query limiting policies or their own policies for content distribution.

TODO: add sample request / response

### Security Considerations

[Andrei] These are currently just notes of the main points.

All callback URLs used in the interactions -- by Subscribers, Hubs, Publishers -- should be [capability URLs](https://www.w3.org/TR/capability-urls/). The capability URLs should not be disclosed by their recipients and should be refreshed periodically (TODO: write down refreshment details). In addition, all communication should be over HTTPS.

Triggering functions should be signed and all triggering functions should be sandboxed. There are three types of concerns to consider when sandboxing triggering functions:
- _resource capabilities_: resource consumption (e.g., memory, CPU, file storage) should be capped;
- _function capabilities_: some functions should not be allowed, such as update operations;
- _communication capabilities_: network access and communication with other triggering functions should not be allowed.

Depending on deployment configurations, Hubs may choose to accept triggering functions only from trusted namespaces. Similarly, Hubs may choose to provide different privileges for different Subscribers. For instance, when using [LDScript](http://ns.inria.fr/sparql-extension/), some Subscribers may be allowed to use triggering functions that perform SPARQL queries or call Java code in trusted namespaces.
