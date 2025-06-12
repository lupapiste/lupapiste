// Without the explicit binding, knockout and thus cljs_-component
// would not work.
(function() {
  "use strict";
  $(function() {
    $("#apply-execution-permit").applyBindings( {} );
  });
})();
