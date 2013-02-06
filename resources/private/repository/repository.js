var repository = (function() {
  "use strict";

  hub.subscribe("load-application", function(e) {
    ajax
      .query("application", {id: e.id})
      .success(function(data) {
        debug("repository: load-application: loaded  " + data.application.id);
        hub.send("application-loaded", {applicationDetails: data});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  });

  return {
    reloadApplication: function(id) { hub.send("load-application", {id: id}); },
    reloadAllApplications: function() { hub.send("load-all-applications"); }
  };
})();
