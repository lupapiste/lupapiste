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

  function loaded(f) {
    hub.subscribe("application-loaded",f);
  }

  return {
    load: load,
    loaded: loaded
  };
  
})();
