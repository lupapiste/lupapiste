var repository = function() {

  hub.subscribe("load-all-applications", function() {
    ajax
      .query("applications")
      .success(function(data) {
        debug("repository: load-all-applications: loaded " + data.applications.length + " applications");
        _.each(data.applications, function(application) {
          hub.send("application-loaded", {application: application});
        });
        hub.send("all-applications-loaded", {applications: data.applications});
      })
      .call();
  });

  hub.subscribe("load-application", function(e) {
    ajax
      .query("application", {id: e.id})
      .success(function(data) {
        debug("repository: load-application: loaded  " + data.application.id);
        hub.send("application-loaded", {application: data.application});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  });

  return {
    reloadApplication: function(id) { hub.send("load-application", {id: id}); },
    reloadAllApplications: function() { hub.send("load-all-applications"); }
  };
}();
