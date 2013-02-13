var repository = (function() {
  "use strict";

  function load(id) {
    ajax
      .query("application", {id: id})
      .success(function(data) {
        debug("loaded application "+data.application.id);
        hub.send("application-loaded", {applicationDetails: data});
      })
      .error(function() { window.location.hash = "!/404"; })
      .call();
  }

  function loaded(f) {
    hub.subscribe("application-loaded",f);
  }

  return {
    load: load,
    loaded: loaded
  };
})();
