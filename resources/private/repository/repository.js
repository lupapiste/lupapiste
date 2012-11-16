var repository = function() {

  hub.subscribe("load-all-applications", function() {
    ajax
      .query("applications")
      .success(function(data) {
        _.each(data.applications, function(application) {
          hub.send("repository-application-reload", {application: application});
        });
        hub.send("repository-reload", {applications: data.applications});
      })
      .call();
  });

  hub.subscribe("load-application", function(e) {
    ajax
      .query("application", {id: e.id})
      .success(function(data) {
        hub.send("repository-application-reload", {application: data.application});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  });

  return {
    reloadAllApplications: function(callback) { hub.send("load-all-applications"); if (callback) callback(); }
  }
}();
