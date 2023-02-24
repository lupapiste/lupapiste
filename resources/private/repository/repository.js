var repository = (function() {
  "use strict";

  var currentlyLoadingId = null;
  var currentQuery = null;

  var loadingSchemas = ajax
    .query("schemas")
    .error(function(e) { error("can't load schemas", e); })
    .call();

  function schemaNotFound(schemas, name, version) {
    var message = "unknown schema, name='" + name + "', version='" + version + "'";
    error(message);
  }

  function findSchema(schemas, name, version) {
    var v = schemas[version] || schemaNotFound(schemas, name, version);
    var s = v[name] || schemaNotFound(schemas, name, version);
    return _.clone(s);
  }

  function getAllOperations(application) {
    return _.filter([application.primaryOperation].concat(application.secondaryOperations), function(item) {
      return !_.isEmpty(item);
    });
  }

  function showApplicationList() {
    pageutil.hideAjaxWait();
    pageutil.openPage("applications");
  }

  function applicationNotFoundDialog() {
    hub.send("show-dialog", {
      id: "dialog-application-load-error",
      ltitle: "error.application-not-found",
      size: "medium",
      component: "yes-no-dialog",
      componentParams: {
        ltext: "error.application-not-accessible",
        lyesTitle: "navigation",
        yesFn: showApplicationList,
        lnoTitle: "logout",
        noFn: function() {hub.send("logout");}
      }});
  }

  function loadingErrorHandler(id, e) {
    currentlyLoadingId = null;
    error("Application " + id + " not found", e);
    applicationNotFoundDialog();
  }

  hub.subscribe({eventType: "dialog-close", id : "dialog-application-load-error"}, function() {
    showApplicationList();
  });

  function doLoad(id, pending, callback, isLightLoad) {
    hub.send( "scrollService::push");
    currentQuery = ajax
      .query("application", {id: id, lang: loc.getCurrentLanguage()})
      .pending(pending || _.noop)
      .error(_.partial(loadingErrorHandler, id))
      .fail(function (jqXHR) {
        if (jqXHR && jqXHR.status > 0) {
          loadingErrorHandler(id, jqXHR);
        }
      })
      .call();


    $.when(loadingSchemas, currentQuery).then(function(schemasResponse, loadingResponse) {
      var schemas = schemasResponse[0].schemas,
          loading = loadingResponse[0],
          application = loading.application;

      hub.send( "schemaFlagsService::setFlags", {flags: loading.schemaFlags});

      function setSchema(doc) {
        var schemaInfo = doc["schema-info"];
        var schema = findSchema(schemas, schemaInfo.name, schemaInfo.version);
        doc.schema = schema;
        doc.schema.info = _.merge(schemaInfo, doc.schema.info);
      }

      if (application) {
        if (application.id === currentlyLoadingId) {
          currentlyLoadingId = null;
          application.allOperations = getAllOperations(application);

          _.each(application.documents || [], function(doc) {
            setSchema(doc);
          });

          _.each(application.tasks || [], setSchema);

          application.verdicts = util.verdictsWithTasks(application);

          var sortedAttachmentTypes = attachmentUtils.sortAttachmentTypes(application.allowedAttachmentTypes);
          application.allowedAttachmentTypes = sortedAttachmentTypes;

          application.tosFunction = application.tosFunction === undefined ? null : application.tosFunction;
          application.kuntalupatunnukset = application.municipalityPermitIds;

          application.propertyIdSource = application.propertyIdSource || "";

          application.tags = application.tags || [];

          hub.send("application-loaded", {applicationDetails: loading, lightLoad: isLightLoad});
          if (_.isFunction(callback)) {
            callback(application);
          }
        } else {
          error("Concurrent loading issue, old id = " + currentlyLoadingId);
        }
      }
    });
  }

  function load(id, pending, callback, lightLoad) {
    if (window.location.hash.indexOf(id) === -1) {
      // Application is not visible, do not load
      return;
    }

    if (currentlyLoadingId) {
      currentQuery.abort();
    }
    currentlyLoadingId = id;
    doLoad(id, pending, callback, lightLoad);
  }

  function lightLoad(id) {
    load(id, null, null, true);
  }

  function loaded(pages, f) {
    if (!_.isFunction(f)) {
      throw "f is not a function: f=" + f;
    }
    hub.subscribe("application-loaded", function(e) {
      if (_.includes(pages, pageutil.getPage())) {
        f(e.applicationDetails.application);
      }
    });
  }

  return {
    load: load,
    lightLoad: lightLoad,
    loaded: loaded,
    applicationNotFoundDialog: applicationNotFoundDialog,
    schemas: loadingSchemas.promise() // for debugging
  };

})();
