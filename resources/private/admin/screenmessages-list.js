;(function() {
  "use strict";

  hub.onPageChange("screenmessages", LUPAPISTE.Screenmessage.refresh);

  $(function() {
    $("#screenmessages").applyBindings(LUPAPISTE.Screenmessage);
  });

})();
