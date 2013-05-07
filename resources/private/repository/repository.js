var repository = (function() {
  "use strict";

  function load(id, pending) {
    ajax
      .query("application", {id: id})
      .pending(pending)
      .success(function(data) {
        hub.send("application-loaded", {applicationDetails: data});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  }

  function loaded(pages, f) {
    hub.subscribe("application-loaded", function(e) {
      if(_.contains(pages, pageutil.getPage())) {
        //TODO: passing details as 2nd param due to application.js hack (details contains the municipality persons)
        f(e.applicationDetails.application, e.applicationDetails);
      }
    });
  }

  return {
    load: load,
    loaded: loaded
  };

})();
