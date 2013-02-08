var repository = (function() {
  "use strict";

  function reloadApplication(id) {
    ajax
      .query("application", {id: id})
      .success(function(data) {
        debug("repository: load-application: loaded  " + data.application.id);
        hub.send("application-loaded", {applicationDetails: data});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  }

  return {
    reloadApplication: reloadApplication
  };
})();
