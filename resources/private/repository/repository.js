var repository = (function() {
  "use strict";

  hub.subscribe("load-all-applications", function() {
    ajax
      .query("applications")
      .success(function(data) {
        debug("repository: load-all-applications: loaded " + data.applications.length + " applications");
        hub.send("all-applications-loaded", {applications: data.applications});
      })
      .call();
  });

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
