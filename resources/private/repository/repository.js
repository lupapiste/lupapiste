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

  function loadingErrorHandler(id, e) {
    currentlyLoadingId = null;
    error("Application " + id + " not found", e);
    LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
  }

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

          application.tags = _(application.tags || []).map(function(tagId) {
            return {id: tagId, label: util.getIn(application, ["organizationMeta", "tags", tagId])};
          }).filter("label").value();

          var sortedAttachmentTypes = attachmentUtils.sortAttachmentTypes(application.allowedAttachmentTypes);
          application.allowedAttachmentTypes = sortedAttachmentTypes;

          application.tosFunction = application.tosFunction === undefined ? null : application.tosFunction;

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

  function showApplicationList() {
    pageutil.hideAjaxWait();
    pageutil.openPage("applications");
  }

  // Cannot be changed to use LUPAPISTE.ModalDialog.showDynamicYesNo, because the id is registered with hub.subscribe.
  LUPAPISTE.ModalDialog.newYesNoDialog("dialog-application-load-error",
      loc("error.application-not-found"), loc("error.application-not-accessible"),
      loc("navigation"), showApplicationList, loc("logout"), function() {hub.send("logout");});

  hub.subscribe({eventType: "dialog-close", id : "dialog-application-load-error"}, function() {
    showApplicationList();
  });

  return {
    load: load,
    loaded: loaded,
    schemas: loadingSchemas.promise() // for debugging
  };

})();
