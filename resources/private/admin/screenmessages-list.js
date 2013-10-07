;(function() {
  "use strict";

  var messagesModel = new LUPAPISTE.Screenmessage();

  hub.onPageChange("screenMessages", messagesModel.refresh);

  $(function() {
    $("#screenMessages").applyBindings(messagesModel);
  });

})();
