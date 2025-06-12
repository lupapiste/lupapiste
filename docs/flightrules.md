#  Flight rules, say what?

A [guide for astronauts](https://www.jsc.nasa.gov/news/columbia/fr_generic.pdf)

It's a collection of advice, HOWTOs and general information on how to do certain things
that come up with Lupapiste development.

## How to use this document

Add second level heading for a topic
Add level three heading for advice, question, a thing you can answer.
Write the answer below that heading

Run [doctoc](https://github.com/thlorenz/doctoc)! That generates to TOC below

```
npm install -g doctoc
doctoc docs/flightrules.md
```

## Table of contents

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Microservices with their own UI](#microservices-with-their-own-ui)
  - [How to share clojurescript-code between Lupapiste and microservice](#how-to-share-clojurescript-code-between-lupapiste-and-microservice)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->


## Microservices with their own UI

### How to share clojurescript-code between Lupapiste and microservice

Create git-repository for the code that you want to share between codebases.
Check out:
[lupapiste/document-search-commons](https://github.com/lupapiste/document-search-commons)

Clone or copy the repository created

Create folder `checkouts` under microservice code base, and symlink the repository you just cloned.

Add entry to folder under `checkouts` to dev :source-path to project.clj on the microservice. Like lupapiste-commons in the example: 

`:cljsbuild {:builds {:dev {:source-paths ["src/cljs" "src/cljc" "test/cljs" "dev/cljs" "checkouts/lupapiste-commons/src"]`

For production build, publish the artifact from the shared codebase (to clojars) and and the dependency to Lupapiste and microservice.
