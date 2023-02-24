// The explicit binding is needed since the send-attachments page is a
// separate view "outside" of the rest of the application. Without
// binding, knockout and thus cljs_-component would not work.
(function() {
  "use strict";

  $(function() {
    $("#send-attachments").applyBindings( {} );
  });
})();
