var repository = (function() {
  "use strict";

  function load(id, pending) {
    ajax
      .query("application", {id: id})
      .pending(pending)
      .success(function(data) {
        hub.send("application-loaded", {applicationDetails: data});
      })
      .error(function(e) {
        error("Application " + id + " not found", e);
        LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
      })
      .call();
  }

  function loaded(pages, f) {
    hub.subscribe("application-loaded", function(e) {
      if(!f || _.contains(pages, pageutil.getPage())) {
        //TODO: passing details as 2nd param due to application.js hack (details contains the municipality persons)
        f(e.applicationDetails.application, e.applicationDetails);
      }
    });
  }

  function showApplicationList() {
    pageutil.hideAjaxWait();
    window.location.hash = "!/applications";
  }

  // Cannot be changed to use LUPAPISTE.ModalDialog.showDynamicYesNo, because the id is registered with hub.subscribe.
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
