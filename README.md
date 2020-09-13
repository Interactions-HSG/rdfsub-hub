# RDFSub Hub

RDFSub is a brokered publish/subscribe protocol for triple patterns inspired by [W3C WebSub](https://www.w3.org/TR/websub/) and [LDScript and Linked Functions](http://ns.inria.fr/sparql-extension/). This project is a Java implementation of an RDFSub Hub based on [Corese](https://github.com/Wimmics/corese) and [Vert.x](https://vertx.io/).

A sketch of the RDFSub protocol is available in [RDFSub.md](RDFSub.md).

## Prerequisites

* JDK 8+

## Running the project

This project uses a version of Corese that is newer than the one published on Maven central. To use the correct version:

1. Get the latest version of Corese from Github: `git clone https://github.com/Wimmics/corese.git`

2. Edit [line 112](https://github.com/Wimmics/corese/blob/925823709b5fe6cb8960ec626397ad2259339357/pom.xml#L112) in `corese/pom.xml` to bump the version from 4.1.1 to 4.1.2.

3. Install this latest version in the local Maven repository: `mvn -Dmaven.test.skip=true install`

This project's build file is configured to work with Corese 4.1.2. After completing the above configuration, you can run the project with:

    ./gradlew test run

The command compiles the project and runs the tests, then it launches the RDFSub Hub. Open your browser to `http://localhost:8090` and you should see a hello-world like message.

To build the project:

    ./gradlew build

This will generate a _fat-jar_ in the `build/libs` directory.

## Quick demo 

Setup: 

This demo requires to mock the HTTP requests for retrieving a subscriber's triggering function and for pushing notifications. One simple solution is to use [MockServer](https://www.mock-server.com/).

A MockServer expectation initialization file for this demo is in `mockserver/mockserver.json`. To run MockServer with [Docker](https://www.docker.com/), you will have to use a bind mount and to set an environment variable like so:

```
docker run -v "$(pwd)"/mockserver/mockserver.json:/tmp/mockserver/mockserver.json \
-e MOCKSERVER_INITIALIZATION_JSON_PATH=/tmp/mockserver/mockserver.json \
-d --rm --name mockserver -p 1080:1080 mockserver/mockserver
```

The above command will run the Docker container in the background and will print the container ID. To stop the container: `docker stop mockserver` 

Running the demo:

1. Start the RDFSub hub: `./gradlew run`

2. Run `./demo/create_subscriptions.sh` to create two subscriptions: one for a SPARQL SELECT query and one for a SPARQL CONSTRUCT query.

3. Start an instance of the [Corese](https://github.com/Wimmics/corese) server (the default port is `8080`). This will be the publisher in our demo.

4. Run `./demo/publisher.sh` to start publishing data.

Both subscriptions use the same callback IRI and the same Linked Function as trigger. When processing the subscriptions, the RDFSub Hub validates the callback IRI and retrieves the Linked Function to check its syntax.