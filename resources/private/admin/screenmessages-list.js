;(function() {
  "use strict";

  var screenMessagesListModel = new (function() {
    var self = this;

    self.textFi = ko.observable("");
    self.textSv = ko.observable("");

    self.disabled = ko.pureComputed(function() {
      return !self.textFi().length;
    });

    self.addMessage = function() {
      LUPAPISTE.Screenmessage.add($("#add-text-fi").val(), $("#add-text-sv").val());
      self.textFi("");
      self.textSv("");
    };

  })();

  hub.onPageLoad("screenmessages", LUPAPISTE.Screenmessage.refresh);

  $(function() {
    $("#screenmessages").applyBindings({
      screenmessages: LUPAPISTE.Screenmessage,
      screenMessagesListModel: screenMessagesListModel
    });
  });

})();
