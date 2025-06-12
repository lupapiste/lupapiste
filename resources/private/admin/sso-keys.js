(function() {
  "use strict";

  $(function() {
    // Indirectly mounts the cljs-component even if there are no bindings as such
    // More details in developerguide.md
    $("#single-sign-on-keys").applyBindings({});
  });

})();
