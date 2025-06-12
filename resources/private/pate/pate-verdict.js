// The explicit binding is needed since the pate-verdict page is a
// separate view "outside" of the rest of the application. Without
// binding, knockout and thus cljs_-component would not work.
(function() {
  "use strict";

  $(function() {
    $("#verdict").applyBindings( {} );
  });
})();
