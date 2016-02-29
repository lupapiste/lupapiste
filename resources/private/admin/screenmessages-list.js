;(function() {
  "use strict";

  function ScreenMessagesListModel() {
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
  }
  var screenMessagesListModel = new ScreenMessagesListModel();

  hub.onPageLoad("screenmessages", LUPAPISTE.Screenmessage.refresh);

  $(function() {
    $("#screenmessages").applyBindings({
      screenmessages: LUPAPISTE.Screenmessage,
      screenMessagesListModel: screenMessagesListModel
    });
  });

})();
