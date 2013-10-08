;(function() {
  "use strict";

  var messagesModel = new LUPAPISTE.Screenmessage();

  hub.onPageChange("screenmessages", messagesModel.refresh);

  $(function() {
    $("#screenmessages").applyBindings(messagesModel);
  });

})();
