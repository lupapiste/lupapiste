if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.Screenmessage = function () {
  var self = this;

  self.messages = ko.observableArray([]);

  self.refresh = function(application) {
    ajax.get("/system/screenmessage")
    .success(function(data) {
      self.messages(data.screenmessages);
    })
    .error(function(e) {
      self.messages([]);
      debug("Could not load screen messages", e);
    })
    .call();
  };
}
