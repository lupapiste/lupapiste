var repository = (function() {
  "use strict";

  var loadingSchemas = ajax
    .query("schemas")
    .error(function(e) { error("can't load schemas"); })
    .call();
  
  function findSchema(name, version) {
    // Sanity check
    if (!name || !vertsion) throw "illegal argument";
    var s = schemas[name] || schemaNotFound(name, version);
    return s[version] || schemaNotFound(name, version);
  }
  
  function schemaNotFound(name, version) {
    // TODO, now what?
    error("unknown schema, name='" + name + "', version='" + version + "'");
    return undefined;
  }
  
  function load(id, pending) {
    var loadingApp = ajax
      .query("application", {id: id})
      .pending(pending)
      .error(function(e) {
        error("Application " + id + " not found", e);
        LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
      })
      .call();
    $.when(loadingSchemas, loadingApp).then(function(schemas, appResponse) {
      console.log("success:", schemas[0], appResponse[0]);
      hub.send("application-loaded", {applicationDetails: appResponse[0]});
    }); 
  }
  
  function loaded(pages, f) {
    if (!_.isFunction(f)) throw "f is not a function: f=" + f;
    hub.subscribe("application-loaded", function(e) {
      if (_.contains(pages, pageutil.getPage())) {
        //TODO: passing details as 2nd param due to application.js hack (details contains the municipality persons)
        f(e.applicationDetails.application, e.applicationDetails);
      }
    });
  }

  function showApplicationList() {
    pageutil.hideAjaxWait();
    window.location.hash = "!/applications";
  }

  LUPAPISTE.ModalDialog.newYesNoDialog("dialog-application-load-error",
      loc("error.application-not-found"), loc("error.application-not-accessible"),
      loc("navigation"), showApplicationList, loc("logout"), function() {hub.send("logout");});

  hub.subscribe({type: "dialog-close", id : "dialog-application-load-error"}, function() {
    showApplicationList();
  });

  return {
    load: load,
    loaded: loaded
  };

})();
