# Introduction

This project uses Maven to generate Java source code files from XML `.xsd` schemas. The generated Java sources and the original schemas are automatically copied up to the parent Lupapiste project in their proper places. Do not manually modify those files, instead always alter the original schemas here under `codegen` and generate a fresh set of Java sources.

# Schemas

## RSS 2.0

The `rss-2_0.xsd` schema was sourced from [rss2schema](https://github.com/jthelin/rss2schema/blob/main/schema/rss-2_0.xsd).

The `lupapiste_extensions.xsd` schema contains various Lupapiste specific extension fields, which can be included in the RSS feed.

## SAP SBO

Schemas related to the SAP SBO invoicing API.

## Onkalo

Schemas related to producing "tiedonohjaus" messages to Onkalo for archiving purposes.

# Plugins

We are targeting JAXB `2.4.x` so we can't update plugins that target `3.x` or `4.x`, which use the new Jakarta EE Java packages/classes.

We use the [cxf-xjc-plugin](https://cxf.apache.org/cxf-xjc-plugin.html) to generate Java sources from `.xsd` schemas.

We use version `2.1.0` of the [jaxb-rich-contract-plugin](https://github.com/mklemm/jaxb-rich-contract-plugin/tree/2.1.0) to enhance the generated Java classes. This is the latest version which still supports JAXB `2.4.x`.

# Common tasks

## Clean, compile and install locally

This will clear the `target` directory, generate fresh Java classes from the schemas, and finally copy the schemas and classes into the main Lupapiste project.

```sh
mvn clean compile
```

## Browse documentation

```sh
mvn clean site:run
```

and open [localhost:8080](http://localhost:8080). Javadoc will be generated on first load.

## Check for outdated dependencies

For libraries:

```sh
mvn versions:display-dependency-updates
```

For maven plugins:

```sh
mvn versions:display-plugin-updates
```
