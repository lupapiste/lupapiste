;(function() {
  "use strict";

  var screenMessagesListModel = new (function() {
    var self = this;

    self.textFi = ko.observable("");
    self.textSv = ko.observable("");

    self.disabled = ko.computed(function() {
      return !self.textFi().length;
    });

  })();

  hub.onPageChange("screenmessages", LUPAPISTE.Screenmessage.refresh);

  $(function() {
    $("#screenmessages").applyBindings({
      screenmessages: LUPAPISTE.Screenmessage,
      screenMessagesListModel: screenMessagesListModel
    });
  });

})();
